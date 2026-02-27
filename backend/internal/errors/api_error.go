package apperrors

import "net/http"

type APIError struct {
	Code       string `json:"code"`
	Message    string `json:"message"`
	StatusCode int    `json:"-"`
}

func (e *APIError) Error() string {
	return e.Message
}

func New(status int, code, message string) *APIError {
	return &APIError{
		Code:       code,
		Message:    message,
		StatusCode: status,
	}
}

func BadRequest(code, message string) *APIError {
	return New(http.StatusBadRequest, code, message)
}

func Unauthorized(code, message string) *APIError {
	return New(http.StatusUnauthorized, code, message)
}

func Conflict(code, message string) *APIError {
	return New(http.StatusConflict, code, message)
}
