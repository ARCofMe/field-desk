import json
from typing import List, Dict, Tuple
from xml.etree import ElementTree
from datetime import date

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
    body = f"""
    <request>
        <serviceRequestAssignmentList>
            <dateRangeStart>{start}</dateRangeStart>
            <dateRangeEnd>{end}</dateRangeEnd>
            <dateRangeType>scheduled</dateRangeType>
            <assignedTo>
                <userId>{tech_id or ''}</userId>
            </assignedTo>
        </serviceRequestAssignmentList>
    </request>
    """

    url = f"{base}/api/2.0/serviceRequests/getAssignmentList.aspx"
    headers = _auth_headers(api_key, account)

    response = requests.post(
        url,
        headers=headers,
        data=body.encode("utf-8"),
        timeout=12,
    )
    if response.status_code != 200:
        raise RuntimeError(f"HTTP {response.status_code}: {response.text[:160]}")

    return _parse_assignment_jobs(response.text)


def get_jobs_json(base_url: str, api_key: str, tech_id: str = "", start_date: str | None = None, end_date: str | None = None) -> str:
    return json.dumps(get_jobs(base_url, api_key, tech_id, start_date, end_date))


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
    for assignment in root.iter("serviceRequestAssignment"):
        sr_id = assignment.findtext("serviceRequestId") or ""
        start = assignment.findtext("startDate") or ""
        end = assignment.findtext("endDate") or ""
        window = _format_window(start, end)
        jobs.append(
            {
                "id": sr_id or (assignment.findtext("assignmentId") or ""),
                "address": assignment.findtext("location") or "Address not provided",
                "appointmentWindow": window or "Unscheduled",
                "customerName": assignment.findtext("assignmentComment") or "Service Request",
                "customerPhone": "",
                "status": "Completed" if assignment.findtext("isComplete") == "true" else "Pending",
                "distanceMiles": None,
            }
        )
    return jobs


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
