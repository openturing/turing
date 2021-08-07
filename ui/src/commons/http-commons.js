import axios from "axios";

var username = 'admin';
var password = 'admin';
var basicAuth = 'Basic ' + btoa(username + ':' + password);

const api = axios.create({
    baseURL: "http://localhost:2710/api/v2",
    headers: {
        'Authorization': basicAuth,
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/json'
    }
});

export default api;