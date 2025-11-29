import json
from typing import List, Dict, Tuple
from xml.etree import ElementTree
from datetime import date
import xml.etree.ElementTree as ET

import requests
from urllib.parse import urlparse

from bluefolder.auth import test_connection


def hello_from_python():
    return "Hello from Python via Chaquopy!"


def get_assignments_for_user(tech_id: str):
    return f"[Dummy] No real assignments for tech {tech_id} (stubbed)"


def check_api_connection(base_url: str, api_key: str) -> str:
    """Round-trip to BlueFolder to validate credentials."""
    if not base_url or not api_key:
        return "Missing base URL or API key"
    return test_connection(_normalize_base_url(base_url), api_key)


def get_jobs(
    base_url: str,
    api_key: str,
    tech_id: str = "",
    start_date: str | None = None,
    end_date: str | None = None,
    date_range_type: str = "scheduled",
) -> List[Dict]:
    """Fetch assignments for a technician using BlueFolder assignments endpoint."""
    if not base_url or not api_key:
        raise ValueError("Missing base URL or API key")

    base = _normalize_base_url(base_url)
    account = _extract_account(base)
    if not account:
        raise ValueError("Unable to parse BlueFolder account from base URL")

    today = date.today().strftime("%Y.%m.%d")
    start = start_date or f"{today} 12:00 AM"
    end = end_date or f"{today} 11:59 PM"

    # Build XML payload with apikey element like the official client
    root = ET.Element("request")
    ET.SubElement(root, "apikey").text = api_key
    sr_list = ET.SubElement(root, "serviceRequestAssignmentList")
    ET.SubElement(sr_list, "dateRangeStart").text = start
    ET.SubElement(sr_list, "dateRangeEnd").text = end
    dr_type_el = ET.SubElement(sr_list, "dateRangeType")
    dr_type_el.text = date_range_type
    if tech_id:
        assigned = ET.SubElement(sr_list, "assignedTo")
        ET.SubElement(assigned, "userId").text = str(tech_id)

    base_body = ET.tostring(root, encoding="utf-8").decode("utf-8")

    url = f"{base}/api/2.0/serviceRequests/getAssignmentList.aspx"
    headers = _auth_headers(api_key, account)

    # Only try the requested date range type. Some BF accounts 404 on other types.
    types_to_try = [date_range_type]

    last_error: str | None = None
    last_response_snippet: str | None = None
    last_status: int | None = None
    for dr_type in types_to_try:
        payload = base_body.replace(f"<dateRangeType>{date_range_type}</dateRangeType>", f"<dateRangeType>{dr_type}</dateRangeType>")
        try:
            response = requests.post(
                url,
                headers=headers,
                data=payload.encode("utf-8"),
                timeout=12,
            )
            last_status = response.status_code
            last_response_snippet = response.text[:1000]
            if response.status_code != 200:
                last_error = f"{dr_type}: HTTP {response.status_code}: {last_response_snippet}"
                continue
            # Some BF accounts return <response status='fail'><error code='404'>Data not found</error></response> with HTTP 200.
            try:
                fail_root = ElementTree.fromstring(response.text)
                if fail_root.get("status") == "fail":
                    err_node = fail_root.find(".//error")
                    code = err_node.get("code") if err_node is not None else ""
                    if code == "404":
                        return []  # No assignments for this range is not an error.
                    # Other fail codes should bubble up with details.
                    last_error = f"{dr_type}: HTTP 200 status=fail code={code} {last_response_snippet}"
                    continue
            except Exception:
                pass
            jobs = _parse_assignment_jobs(response.text)
            # Enrich missing addresses/customer names via serviceRequest.get if needed
            jobs = _hydrate_jobs_from_service_requests(base, api_key, account, jobs)
            if jobs:
                return jobs
            # 200 OK but no jobs parsed
            last_error = (
                f"{dr_type}: HTTP 200 but no assignments parsed. "
                f"userId={tech_id or '(none)'} payload={payload[:200]} "
                f"response={last_response_snippet}"
            )
        except Exception as exc:
            last_error = f"{dr_type}: {exc}"
            continue

    if last_error:
        raise RuntimeError(
            f"Assignment fetch failed for user '{tech_id}' {start_date}→{end_date} type={types_to_try} :: {last_error}"
        )
    raise RuntimeError(
        f"No assignments returned for user '{tech_id}' {start_date}→{end_date} type={types_to_try} (status={last_status}). Last response: {last_response_snippet}"
    )


def get_jobs_json(
    base_url: str,
    api_key: str,
    tech_id: str = "",
    start_date: str | None = None,
    end_date: str | None = None,
    date_range_type: str = "scheduled",
) -> str:
    jobs = get_jobs(base_url, api_key, tech_id, start_date, end_date, date_range_type)
    return json.dumps(jobs)


def _parse_jobs(xml_text: str) -> List[Dict]:
    jobs: List[Dict] = []
    root = ElementTree.fromstring(xml_text)
    for work_order in root.iter("workorder"):
        id_text = _get_text(work_order, ["ID", "WorkOrderID"]) or "Unknown"
        status = _get_text(work_order, ["Status"])
        customer = _get_text(work_order, ["CustomerName", "AccountName", "Company"])
        phone = _get_text(work_order, ["CustomerPhone", "Phone", "ContactPhone"])
        start = _get_text(work_order, ["ScheduledStartDateTime", "StartDate", "DateScheduled"])
        end = _get_text(work_order, ["ScheduledEndDateTime", "EndDate"])
        window = _format_window(start, end)
        address_parts = [
            _get_text(work_order, ["Address1", "Address"]),
            _get_text(work_order, ["Address2"]),
            _city_state_zip(work_order),
        ]
        address = ", ".join([p for p in address_parts if p])

        jobs.append(
            {
                "id": id_text,
                "address": address or "No address",
                "appointmentWindow": window or "Unscheduled",
                "customerName": customer or "Unknown customer",
                "customerPhone": phone or "",
                "status": status or "Unknown",
                "distanceMiles": None,
            }
        )
    return jobs


def _parse_assignment_jobs(xml_text: str) -> List[Dict]:
    jobs: List[Dict] = []
    root = ElementTree.fromstring(xml_text)

    for assignment in root.findall(".//serviceRequestAssignment"):
        # Handle namespaces by matching suffix on descendants, not just direct children
        sr_id = _get_text_suffix(assignment, ["serviceRequestId", "ServiceRequestId", "serviceRequestID"]) or ""
        start = _normalize_dt(_get_text_suffix(assignment, ["startDate", "start"]) or "")
        end = _normalize_dt(_get_text_suffix(assignment, ["endDate", "end"]) or "")
        window = _format_window(start, end)
        customer_id = _get_text_suffix(assignment, ["customerId", "CustomerId", "customer"])
        location_id = _get_text_suffix(assignment, ["customerLocationId", "CustomerLocationId", "locationId", "LocationId"])

        # Location fields may vary; try nested address parts first, then any location text
        address = (
            _build_address_from_children(assignment)
            or _get_text_suffix(assignment, ["location", "Location", "address"])
            or "Address not provided"
        )
        if sr_id == "92555":
            print(f"[BF][SR92555] assignment address candidate: {address}")

        raw_complete = _get_text_suffix(assignment, ["isComplete", "complete"])
        is_complete = str(raw_complete).strip().lower() in ("true", "1", "yes", "y")

        jobs.append(
            {
                "id": sr_id or (_get_text_suffix(assignment, ["assignmentId", "AssignmentId"]) or ""),
                "serviceRequestId": sr_id,
                "customerId": customer_id,
                "customerLocationId": location_id,
                "address": address,
                "appointmentWindow": window or "Unscheduled",
                "customerName": _get_text_suffix(assignment, ["assignmentComment", "comment", "subject"]) or f"Service Request {sr_id or ''}".strip(),
                "customerPhone": _get_text_suffix(assignment, ["phone", "customerPhone"]) or "",
                "status": "Completed" if is_complete else "Pending",
                "equipment": _get_text_suffix(assignment, ["equipment", "Equipment", "asset", "Asset"]),
                "distanceMiles": None,
            }
        )

    return jobs


def _hydrate_jobs_from_service_requests(base_url: str, api_key: str, account: str, jobs: List[Dict]) -> List[Dict]:
    """
    For any job missing address/customer info, call serviceRequests/get.aspx to hydrate.
    If customer/location IDs exist, prefer the location address over bill-to addresses.
    Cached per serviceRequestId to minimize calls.
    """
    cache: Dict[str, Dict] = {}
    for job in jobs:
        sr_id = job.get("serviceRequestId") or job.get("id")
        if not sr_id:
            continue
        if sr_id not in cache:
            cache[sr_id] = _fetch_service_request(base_url, api_key, account, sr_id)
        details = cache.get(sr_id) or {}
        # Prefer customer location address when IDs exist (over bill-to in SR)
        cust_id = details.get("customerId") or job.get("customerId")
        loc_id = details.get("customerLocationId") or job.get("customerLocationId")

        loc_addr = None
        if cust_id and loc_id:
            loc_addr = _fetch_customer_location(base_url, api_key, account, cust_id, loc_id)

        new_addr = loc_addr or details.get("address") or job.get("address")

        if new_addr and new_addr.strip():
            job["address"] = new_addr
        else:
            job["address"] = "Address not provided"
        job["customerName"] = details.get("customerName") or job.get("customerName")
        job["appointmentWindow"] = details.get("window") or job.get("appointmentWindow")
        if details.get("equipment"):
            job["equipment"] = details["equipment"]
    return jobs


def _fetch_service_request(base_url: str, api_key: str, account: str, sr_id: str | int) -> Dict:
    try:
        root = ET.Element("request")
        ET.SubElement(root, "apikey").text = api_key
        ET.SubElement(root, "serviceRequestId").text = str(sr_id)
        xml_body = ET.tostring(root, encoding="utf-8")
        url = f"{base_url}/api/2.0/serviceRequests/get.aspx"
        headers = _auth_headers(api_key, account)
        resp = requests.post(url, headers=headers, data=xml_body, timeout=12)
        if resp.status_code != 200:
            return {}
        xml = ElementTree.fromstring(resp.text)
        sr = xml.find(".//serviceRequest")
        if sr is None:
            return {}
        # Prefer explicit customer location node, then fallback to SR-level address fields
        address = _extract_address(sr)
        # If a customer location is present, prefer its fields over bill-to even if generic fallback picked bill-to.
        loc_only = _extract_customer_location_only(sr)
        if loc_only:
            address = loc_only
        if str(sr_id) == "92555":
            snippet = ElementTree.tostring(sr, encoding="unicode")[:2000]
            print(f"[BF][SR92555] SR XML snippet: {snippet}")
            print(f"[BF][SR92555] chosen SR address: {address}")
        customer = _get_text_suffix(sr, ["customerName", "accountName", "company"]) or ""
        subj = _get_text_suffix(sr, ["description", "subject"]) or ""
        start = _normalize_dt(_get_text_suffix(sr, ["ScheduledStartDateTime", "StartDate", "DateScheduled"]) or "")
        end = _normalize_dt(_get_text_suffix(sr, ["ScheduledEndDateTime", "EndDate"]) or "")
        window = _format_window(start, end)
        equipment = (
            _get_text_suffix(sr, ["equipment", "Equipment", "asset", "Asset", "name", "description"])
            or ""
        )
        return {
            "address": address,
            "customerName": customer or subj or f"Service Request {sr_id}",
            "window": window,
            "equipment": equipment,
            "customerId": _get_text_suffix(sr, ["customerId", "CustomerId", "customer"]),
            "customerLocationId": _get_text_suffix(sr, ["customerLocationId", "CustomerLocationId", "locationId", "LocationId"]),
        }
    except Exception:
        return {}


def _get_text_suffix(element: ElementTree.Element, tags: List[str]) -> str:
    """
    Find the first descendant tag whose name ends with any provided suffix.
    Handles namespaces by suffix matching and searches nested children.
    """
    for child in element.iter():
        for t in tags:
            if child.tag.lower().endswith(t.lower()):
                if child.text and child.text.strip():
                    return child.text.strip()
    return ""


def _build_address_from_children(element: ElementTree.Element) -> str:
    parts = []
    street_main = _get_text_suffix(
        element,
        [
            "addressStreet",
            "address1",
            "addressLine1",
            "address",
            "street",
            "line1",
        ],
    )
    street2 = _get_text_suffix(element, ["address2", "addressLine2", "line2"])
    city = _get_text_suffix(element, ["addressCity", "city"])
    state = _get_text_suffix(element, ["addressState", "state"])
    postal = _get_text_suffix(element, ["addressPostalCode", "zip", "postalCode"])

    for p in [street_main, street2, city, state, postal]:
        if p:
            parts.append(p)
    return ", ".join(parts)


def _extract_account(base_url: str) -> str:
    """Given https://mycompany.bluefolder.com, return mycompany."""
    try:
        host = urlparse(base_url).hostname or ""
        if host.endswith(".bluefolder.com"):
            return host.split(".")[0]
    except Exception:
        pass
    return ""


def _get_text(element: ElementTree.Element, tags: List[str]) -> str:
    for tag in tags:
        found = element.find(tag)
        if found is not None and (found.text or "").strip():
            return found.text.strip()
    return ""


def _city_state_zip(element: ElementTree.Element) -> str:
    city = _get_text(element, ["City"])
    state = _get_text(element, ["State"])
    postal = _get_text(element, ["Zip", "PostalCode"])
    parts = []
    if city:
        parts.append(city)
    if state:
        parts.append(state)
    if postal:
        parts.append(postal)
    return " ".join(parts).strip()


def _format_window(start: str, end: str) -> str:
    if start and end:
        return f"{start} - {end}"
    return start or end


def _auth_headers(api_key: str, account: str) -> Dict[str, str]:
    import base64

    token = base64.b64encode(f"{api_key}:{account}".encode("utf-8")).decode("utf-8")
    return {
        "Content-Type": "application/xml",
        "Authorization": f"Basic {token}",
    }


def _normalize_base_url(base_url: str) -> str:
    trimmed = base_url.strip()
    if trimmed.startswith("http://") or trimmed.startswith("https://"):
        return trimmed.rstrip("/")
    return f"https://{trimmed}".rstrip("/")


def _normalize_dt(dt: str) -> str:
    return dt.replace("T", " ").replace("Z", "").strip()


def _extract_address(sr_node: ElementTree.Element) -> str:
    """Prefer customerLocation in SR payload to avoid picking up billing/other addresses."""
    for path in [".//customerLocation", ".//location", ".//address", ".//Address"]:
        node = sr_node.find(path)
        if node is not None:
            addr = _build_address_from_children(node)
            if addr:
                return addr

    # As a fallback, gather street/city/state/zip but ignore billTo* fields.
    parts = {}
    for el in sr_node.iter():
        tag = el.tag.lower()
        if "billto" in tag:
            continue
        text = (el.text or "").strip()
        if not text:
            continue
        if tag.endswith("addressstreet"):
            parts["street"] = text
        elif tag.endswith("addresscity"):
            parts["city"] = text
        elif tag.endswith("addressstate"):
            parts["state"] = text
        elif tag.endswith("address2") or tag.endswith("addressline2") or tag.endswith("line2"):
            parts["street2"] = text
        elif tag.endswith("addresspostalcode") or tag.endswith("postalcode") or tag.endswith("zip"):
            parts["zip"] = text
    if parts and any(parts.get(k) for k in ("street", "street2", "city", "state")):
        return ", ".join(
            [
                parts.get("street", ""),
                parts.get("street2", ""),
                parts.get("city", ""),
                parts.get("state", ""),
                parts.get("zip", ""),
            ]
        ).replace(" ,", "").strip(" ,")

    # Last resort: allow generic builder (may include billTo if nothing else exists)
    return _build_address_from_children(sr_node)


def _extract_customer_location_only(sr_node: ElementTree.Element) -> str:
    """
    Extract address strictly from customerLocation* fields on the SR (ignoring bill-to).
    """
    street = _get_text_suffix(sr_node, ["customerLocationStreetAddress"])
    city = _get_text_suffix(sr_node, ["customerLocationCity"])
    state = _get_text_suffix(sr_node, ["customerLocationState"])
    postal = _get_text_suffix(sr_node, ["customerLocationPostalCode"])
    parts = [p for p in [street, city, state, postal] if p]
    return ", ".join(parts)


def _fetch_customer_location(base_url: str, api_key: str, account: str, customer_id: str | int, location_id: str | int) -> str | None:
    """
    Fetch a customer location directly to get an accurate service address.
    """
    try:
        root = ET.Element("request")
        ET.SubElement(root, "apikey").text = api_key
        ET.SubElement(root, "customerId").text = str(customer_id)
        ET.SubElement(root, "locationId").text = str(location_id)
        ET.SubElement(root, "customerLocationId").text = str(location_id)
        xml_body = ET.tostring(root, encoding="utf-8")
        url = f"{base_url}/api/2.0/customers/getLocation.aspx"
        headers = _auth_headers(api_key, account)
        resp = requests.post(url, headers=headers, data=xml_body, timeout=12)
        if resp.status_code != 200:
            return None
        xml = ElementTree.fromstring(resp.text)
        loc = xml.find(".//customerLocation") or xml.find(".//location")
        if loc is None:
            return None
        addr = _build_address_from_children(loc)
        if addr:
            return addr
        # Try direct fields as last resort
        street = _get_text_suffix(loc, ["addressStreet", "address", "street", "address1", "addressLine1"])
        city = _get_text_suffix(loc, ["addressCity", "city"])
        state = _get_text_suffix(loc, ["addressState", "state"])
        postal = _get_text_suffix(loc, ["addressPostalCode", "zip", "postalCode"])
        parts = [p for p in [street, city, state, postal] if p]
        return ", ".join(parts) if parts else None
    except Exception:
        return None
