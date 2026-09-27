// The Java backend serves these pages and the API from the same process and port
// (Factor VII), so API calls always go back to wherever this page was loaded from.
const API_BASE = window.location.origin;
