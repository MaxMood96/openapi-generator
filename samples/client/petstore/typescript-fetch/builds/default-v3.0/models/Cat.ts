/* tslint:disable */
/* eslint-disable */
/**
 * OpenAPI Petstore
 * This spec is mainly for testing Petstore server and contains fake endpoints, models. Please do not use this for any other purpose. Special characters: \" \\
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { mapValues } from '../runtime';
import type { Animal } from './Animal';
import {
    AnimalFromJSON,
    AnimalFromJSONTyped,
    AnimalToJSON,
    AnimalToJSONTyped,
} from './Animal';

/**
 * 
 * @export
 * @interface Cat
 */
export interface Cat extends Animal {
    /**
     * 
     * @type {boolean}
     * @memberof Cat
     */
    declawed?: boolean;
}

/**
 * Check if a given object implements the Cat interface.
 */
export function instanceOfCat(value: object): value is Cat {
    return true;
}

export function CatFromJSON(json: any): Cat {
    return CatFromJSONTyped(json, false);
}

export function CatFromJSONTyped(json: any, ignoreDiscriminator: boolean): Cat {
    if (json == null) {
        return json;
    }
    return {
        ...AnimalFromJSONTyped(json, true),
        'declawed': json['declawed'] == null ? undefined : json['declawed'],
    };
}

export function CatToJSON(json: any): Cat {
    return CatToJSONTyped(json, false);
}

export function CatToJSONTyped(value?: Cat | null, ignoreDiscriminator: boolean = false): any {
    if (value == null) {
        return value;
    }

    return {
        ...AnimalToJSONTyped(value, true),
        'declawed': value['declawed'],
    };
}

