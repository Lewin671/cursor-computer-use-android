package handler

import (
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"

	apperrors "pomodoro-sync/backend/internal/errors"
	"pomodoro-sync/backend/internal/middleware"
	"pomodoro-sync/backend/internal/service"
)

type TimerHandler struct {
	timerService *service.TimerService
}

func NewTimerHandler(timerService *service.TimerService) *TimerHandler {
	return &TimerHandler{timerService: timerService}
}

type startTimerRequest struct {
	Mode          string `json:"mode" binding:"required"`
	DurationSec   int64  `json:"durationSec" binding:"required"`
	ClientVersion int64  `json:"clientVersion" binding:"required"`
}

type versionRequest struct {
	ClientVersion int64 `json:"clientVersion" binding:"required"`
}

func (h *TimerHandler) GetState(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	state, err := h.timerService.GetState(c.Request.Context(), userID)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, state)
}

func (h *TimerHandler) Start(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	var req startTimerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, apperrors.BadRequest("invalid_request", "request body is invalid"))
		return
	}
	state, err := h.timerService.Start(c.Request.Context(), userID, req.Mode, req.DurationSec, req.ClientVersion)
	if err != nil {
		h.respondMaybeConflict(c, err)
		return
	}
	c.JSON(http.StatusOK, state)
}

func (h *TimerHandler) Pause(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	var req versionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, apperrors.BadRequest("invalid_request", "request body is invalid"))
		return
	}
	state, err := h.timerService.Pause(c.Request.Context(), userID, req.ClientVersion)
	if err != nil {
		h.respondMaybeConflict(c, err)
		return
	}
	c.JSON(http.StatusOK, state)
}

func (h *TimerHandler) Reset(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	var req versionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, apperrors.BadRequest("invalid_request", "request body is invalid"))
		return
	}
	state, err := h.timerService.Reset(c.Request.Context(), userID, req.ClientVersion)
	if err != nil {
		h.respondMaybeConflict(c, err)
		return
	}
	c.JSON(http.StatusOK, state)
}

func (h *TimerHandler) Complete(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	var req versionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, apperrors.BadRequest("invalid_request", "request body is invalid"))
		return
	}
	state, err := h.timerService.Complete(c.Request.Context(), userID, req.ClientVersion)
	if err != nil {
		h.respondMaybeConflict(c, err)
		return
	}
	c.JSON(http.StatusOK, state)
}

func (h *TimerHandler) History(c *gin.Context) {
	userID := c.GetString(middleware.ContextUserIDKey)
	since := int64(0)
	limit := 100
	if rawSince := c.Query("since"); rawSince != "" {
		if parsed, err := strconv.ParseInt(rawSince, 10, 64); err == nil {
			since = parsed
		}
	}
	if rawLimit := c.Query("limit"); rawLimit != "" {
		if parsed, err := strconv.Atoi(rawLimit); err == nil {
			limit = parsed
		}
	}
	items, err := h.timerService.ListHistory(c.Request.Context(), userID, since, limit)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"items":      items,
		"serverTime": timeNowUnix(),
	})
}

func (h *TimerHandler) respondMaybeConflict(c *gin.Context, err error) {
	conflict, ok := err.(*service.VersionConflictError)
	if ok {
		c.JSON(http.StatusConflict, gin.H{
			"code":         "version_conflict",
			"message":      "timer state has been modified on another device",
			"currentState": conflict.CurrentState,
		})
		return
	}
	respondError(c, err)
}

func timeNowUnix() int64 {
	return time.Now().Unix()
}
