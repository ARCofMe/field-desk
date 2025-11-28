import requests


def test_connection(company_url: str, api_key: str):
    """Checks if API key works by calling a simple endpoint."""
    try:
        url = f"{company_url}/api/2.0/users/list.aspx"

        r = requests.post(
            url,
            headers={"Content-Type": "application/xml"},
            data=f"<request><ApiKey>{api_key}</ApiKey></request>",
            timeout=8,
        )

        if r.status_code == 200:
            return "API connection successful"
        else:
            return f"API error: {r.status_code} – {r.text[:200]}"

    except Exception as e:
        return f"Connection failed: {str(e)}"
