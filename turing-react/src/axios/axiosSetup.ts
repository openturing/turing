import axios, {AxiosInstance, AxiosRequestConfig} from 'axios';

const initialization = (config: AxiosRequestConfig): AxiosInstance => {
    return axios.create(config);
};

export default initialization;
