package router

import (
	"net/http"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"

	"pomodoro-sync/backend/internal/config"
	"pomodoro-sync/backend/internal/handler"
	"pomodoro-sync/backend/internal/middleware"
)

func New(
	cfg config.Config,
	authHandler *handler.AuthHandler,
	timerHandler *handler.TimerHandler,
) *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger())
	r.Use(gin.Recovery())
	r.Use(cors.New(cors.Config{
		AllowOrigins:     cfg.CORSOrigins,
		AllowMethods:     []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Authorization", "Content-Type"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: true,
	}))

	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	api := r.Group("/api/v1")
	{
		auth := api.Group("/auth")
		auth.POST("/register", authHandler.Register)
		auth.POST("/login", authHandler.Login)
		auth.POST("/refresh", authHandler.Refresh)
		auth.POST("/logout", authHandler.Logout)

		timer := api.Group("/timer")
		timer.Use(middleware.JWTAuth(cfg.JWTSecret))
		timer.GET("/state", timerHandler.GetState)
		timer.POST("/start", timerHandler.Start)
		timer.POST("/pause", timerHandler.Pause)
		timer.POST("/reset", timerHandler.Reset)
		timer.POST("/complete", timerHandler.Complete)
		timer.GET("/history", timerHandler.History)
	}

	return r
}
