// Code generated by OpenAPI Generator (https://openapi-generator.tech); DO NOT EDIT.

/*
 * Simple no path and body param spec
 *
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * API version: 1.0.0
 */

package petstoreserver

import (
	"encoding/json"
	"net/http"
	"strings"
)

// BodyAPIController binds http requests to an api service and writes the service results to the http response
type BodyAPIController struct {
	service BodyAPIServicer
	errorHandler ErrorHandler
}

// BodyAPIOption for how the controller is set up.
type BodyAPIOption func(*BodyAPIController)

// WithBodyAPIErrorHandler inject ErrorHandler into controller
func WithBodyAPIErrorHandler(h ErrorHandler) BodyAPIOption {
	return func(c *BodyAPIController) {
		c.errorHandler = h
	}
}

// NewBodyAPIController creates a default api controller
func NewBodyAPIController(s BodyAPIServicer, opts ...BodyAPIOption) *BodyAPIController {
	controller := &BodyAPIController{
		service:      s,
		errorHandler: DefaultErrorHandler,
	}

	for _, opt := range opts {
		opt(controller)
	}

	return controller
}

// Routes returns all the api routes for the BodyAPIController
func (c *BodyAPIController) Routes() Routes {
	return Routes{
		"Body": Route{
			strings.ToUpper("Post"),
			"/body/endpoint",
			c.Body,
		},
	}
}

// Body - summary
func (c *BodyAPIController) Body(w http.ResponseWriter, r *http.Request) {
	var bodyRequestParam BodyRequest
	d := json.NewDecoder(r.Body)
	d.DisallowUnknownFields()
	if err := d.Decode(&bodyRequestParam); err != nil {
		c.errorHandler(w, r, &ParsingError{Err: err}, nil)
		return
	}
	if err := AssertBodyRequestRequired(bodyRequestParam); err != nil {
		c.errorHandler(w, r, err, nil)
		return
	}
	if err := AssertBodyRequestConstraints(bodyRequestParam); err != nil {
		c.errorHandler(w, r, err, nil)
		return
	}
	result, err := c.service.Body(r.Context(), bodyRequestParam)
	// If an error occurred, encode the error with the status code
	if err != nil {
		c.errorHandler(w, r, err, &result)
		return
	}
	// If no error, encode the body and the result code
	_ = EncodeJSONResponse(result.Body, &result.Code, result.Headers, w)
}
