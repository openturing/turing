import axios from "axios";
import type { TurSNChat } from "../models/sn-chat.model";
import type { TurSNSearch } from "../models/sn-search.model";

interface QueryOptions {
  q: string;
  p?: string;
  _setlocale?: string;
  sort?: string;
  fq?: string[];
  tr?: string[];
  nfpr?: string;
}

export class TurSNSearchService {
  static query(
    turSiteName: string,
    options: QueryOptions,
  ): Promise<TurSNSearch> {
    const queryString = this.generateQueryString(
      options.q,
      options.p || "",
      options._setlocale || "",
      options.sort || "",
      options.fq || [],
      options.tr || [],
      options.nfpr || "",
    );
    return axios
      .get<TurSNSearch>(`/sn/${turSiteName}/search?${queryString}`)
      .then((response) => response.data);
  }

  static chat(turSiteName: string, options: QueryOptions): Promise<TurSNChat> {
    const queryString = this.generateQueryString(
      options.q,
      options.p || "",
      options._setlocale || "",
      options.sort || "",
      options.fq || [],
      options.tr || [],
      options.nfpr || "",
    );
    return axios
      .get<TurSNChat>(`/sn/${turSiteName}/chat?${queryString}`)
      .then((response) => response.data);
  }

  static autoComplete(
    turSiteName: string,
    options: QueryOptions,
  ): Promise<string[]> {
    const queryString = this.generateQueryString(
      options.q,
      options.p || "",
      options._setlocale || "",
      options.sort || "",
      options.fq || [],
      options.tr || [],
      options.nfpr || "",
    );
    return axios
      .get<string[]>(`/sn/${turSiteName}/ac?${queryString}`)
      .then((response) => response.data);
  }

  static generateQueryString(
    q: string,
    p: string,
    _setlocale: string,
    sort: string,
    fq: string[],
    tr: string[],
    nfpr: string,
  ): string {
    const params = new URLSearchParams();

    params.append("q", q || "*");
    params.append("p", p || "1");

    if (_setlocale) {
      params.append("_setlocale", _setlocale);
    }

    params.append("sort", sort || "relevance");

    this.appendArrayParam(params, "fq", fq);
    this.appendArrayParam(params, "tr", tr);

    if (nfpr) {
      params.append("nfpr", nfpr);
    }

    return params.toString().replaceAll("%5B%5D", "[]");
  }

  private static appendArrayParam(
    params: URLSearchParams,
    key: string,
    value: string | string[],
  ): void {
    if (!value) return;

    const items = Array.isArray(value) ? value : [value];
    items.forEach((item) => params.append(`${key}[]`, item));
  }
}
