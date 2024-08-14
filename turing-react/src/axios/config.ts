import { AxiosRequestConfig } from 'axios';

export const axiosRequestConfiguration: AxiosRequestConfig = {
    baseURL: 'http://localhost:2700/',
    responseType: 'json',
    headers: {
        'Content-Type': 'application/json',
    },
    auth: {
        username: "admin",
        password: "admin"
    }
};
