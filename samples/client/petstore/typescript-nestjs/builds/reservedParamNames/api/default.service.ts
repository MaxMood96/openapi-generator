/**
 * Reserved param names
 * Test reserved param names
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */
/* tslint:disable:no-unused-variable member-ordering */

import { Injectable, Optional } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { AxiosResponse } from 'axios';
import { Observable, from, of, switchMap } from 'rxjs';
import { Configuration } from '../configuration';
import { COLLECTION_FORMATS } from '../variables';

/**
 * Request parameters for testReservedParamNames operation in DefaultService.
 * @export
 * @interface DefaultServiceTestReservedParamNamesRequest
 */
export interface DefaultServiceTestReservedParamNamesRequest {
    /**
     * Should not be treated as a reserved param name
     * @type {string}
     * @memberof DefaultServiceTestReservedParamNames
     */
    readonly notReserved: string

    /**
     * Might conflict with rxjs import
     * @type {string}
     * @memberof DefaultServiceTestReservedParamNames
     */
    readonly 'from': string

    /**
     * Might conflict with headers const
     * @type {string}
     * @memberof DefaultServiceTestReservedParamNames
     */
    readonly 'headers': string
}


@Injectable()
export class DefaultService {

    protected basePath = 'http://localhost';
    public defaultHeaders: Record<string,string> = {};
    public configuration = new Configuration();
    protected httpClient: HttpService;

    constructor(httpClient: HttpService, @Optional() configuration: Configuration) {
        this.configuration = configuration || this.configuration;
        this.basePath = configuration?.basePath || this.basePath;
        this.httpClient = configuration?.httpClient || httpClient;
    }

    /**
     * @param consumes string[] mime-types
     * @return true: consumes contains 'multipart/form-data', false: otherwise
     */
    private canConsumeForm(consumes: string[]): boolean {
        const form = 'multipart/form-data';
        return consumes.includes(form);
    }

    /**
     * Test reserved param names
     * 
     * @param {DefaultServiceTestReservedParamNamesRequest} requestParameters Request parameters.
     */
    public testReservedParamNames(requestParameters: DefaultServiceTestReservedParamNamesRequest, ): Observable<AxiosResponse<any>>;
    public testReservedParamNames(requestParameters: DefaultServiceTestReservedParamNamesRequest, ): Observable<any> {
        const {
            notReserved,
            'from': _from,
            'headers': _headers,
        } = requestParameters;

        if (notReserved === null || notReserved === undefined) {
            throw new Error('Required parameter notReserved was null or undefined when calling testReservedParamNames.');
        }

        if (_from === null || _from === undefined) {
            throw new Error('Required parameter _from was null or undefined when calling testReservedParamNames.');
        }

        if (_headers === null || _headers === undefined) {
            throw new Error('Required parameter _headers was null or undefined when calling testReservedParamNames.');
        }

        let queryParameters = new URLSearchParams();
        if (notReserved !== undefined && notReserved !== null) {
            queryParameters.append('notReserved', <any>notReserved);
        }
        if (_from !== undefined && _from !== null) {
            queryParameters.append('from', <any>_from);
        }

        let headers = {...this.defaultHeaders};
        if (_headers !== undefined && _headers !== null) {
            headers['headers'] = String(_headers);
        }

        let accessTokenObservable: Observable<any> = of(null);

        // authentication (bearerAuth) required
        if (typeof this.configuration.accessToken === 'function') {
            accessTokenObservable = from(Promise.resolve(this.configuration.accessToken()));
        } else if (this.configuration.accessToken) {
            accessTokenObservable = from(Promise.resolve(this.configuration.accessToken));
        }
        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
        ];
        const httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers['Accept'] = httpHeaderAcceptSelected;
        }

        // to determine the Content-Type header
        const consumes: string[] = [
        ];
        return accessTokenObservable.pipe(
            switchMap((accessToken) => {
                if (accessToken) {
                    headers['Authorization'] = `Bearer ${accessToken}`;
                }

                return this.httpClient.post<any>(`${this.basePath}/test`,
                    null,
                    {
                        params: queryParameters,
                        withCredentials: this.configuration.withCredentials,
                        headers: headers
                    }
                );
            })
        );
    }
}
