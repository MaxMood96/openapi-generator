/**
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech) (7.12.0-SNAPSHOT).
 * https://openapi-generator.tech
 * Do not edit the class manually.
*/
package org.openapitools.api

import org.openapitools.model.Pet
import io.swagger.v3.oas.annotations.*
import io.swagger.v3.oas.annotations.enums.*
import io.swagger.v3.oas.annotations.media.*
import io.swagger.v3.oas.annotations.responses.*
import io.swagger.v3.oas.annotations.security.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

import org.springframework.web.bind.annotation.*
import org.springframework.validation.annotation.Validated
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.beans.factory.annotation.Autowired

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

import kotlin.collections.List
import kotlin.collections.Map

@RestController
@Validated
interface FakeApi {

    @Operation(
        tags = ["fake",],
        summary = "",
        operationId = "fakeCookieSuggestion",
        description = """Test list of objects with additional values matching data from cookie""",
        responses = [
            ApiResponse(responseCode = "200", description = "List of pets resolved from suggestion", content = [Content(array = ArraySchema(schema = Schema(implementation = Pet::class)))])
        ]
    )
    @RequestMapping(
            method = [RequestMethod.GET],
            value = ["/fake/cookie-suggestion"],
            produces = ["application/json"]
    )
    fun fakeCookieSuggestion(@NotNull @CookieValue(name = "category.history") categoryHistory: kotlin.String): ResponseEntity<List<Pet>> {
        return ResponseEntity(HttpStatus.NOT_IMPLEMENTED)
    }
}
