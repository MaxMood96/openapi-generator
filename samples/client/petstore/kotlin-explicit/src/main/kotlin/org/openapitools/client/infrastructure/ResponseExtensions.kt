package org.openapitools.client.infrastructure

import okhttp3.Response

/**
 * Provides an extension to evaluation whether the response is a 1xx code
 */
public val Response.isInformational : Boolean get() = this.code in 100..199

/**
 * Provides an extension to evaluation whether the response is a 3xx code
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public val Response.isRedirect : Boolean get() = this.code in 300..399

/**
 * Provides an extension to evaluation whether the response is a 4xx code
 */
public val Response.isClientError : Boolean get() = this.code in 400..499

/**
 * Provides an extension to evaluation whether the response is a 5xx (Standard) through 999 (non-standard) code
 */
public val Response.isServerError : Boolean get() = this.code in 500..999
