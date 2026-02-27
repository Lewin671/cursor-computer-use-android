package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"

	apperrors "pomodoro-sync/backend/internal/errors"
)

func respondError(c *gin.Context, err error) {
	if err == nil {
		return
	}
	if apiErr, ok := err.(*apperrors.APIError); ok {
		c.JSON(apiErr.StatusCode, gin.H{
			"code":    apiErr.Code,
			"message": apiErr.Message,
		})
		return
	}
	c.JSON(http.StatusInternalServerError, gin.H{
		"code":    "internal_error",
		"message": "internal server error",
	})
}
