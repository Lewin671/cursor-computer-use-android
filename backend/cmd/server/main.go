package main

import (
	"log"

	"pomodoro-sync/backend/internal/config"
	"pomodoro-sync/backend/internal/db"
	"pomodoro-sync/backend/internal/handler"
	"pomodoro-sync/backend/internal/repository"
	"pomodoro-sync/backend/internal/router"
	"pomodoro-sync/backend/internal/service"
)

func main() {
	cfg := config.Load()

	dbConn, err := db.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("open db failed: %v", err)
	}
	defer dbConn.Close()

	if err := db.RunMigrations(dbConn, "migrations"); err != nil {
		log.Fatalf("run migrations failed: %v", err)
	}

	userRepo := repository.NewUserRepository(dbConn)
	refreshRepo := repository.NewRefreshTokenRepository(dbConn)
	timerRepo := repository.NewTimerRepository(dbConn)
	historyRepo := repository.NewHistoryRepository(dbConn)

	authService := service.NewAuthService(cfg, userRepo, refreshRepo)
	timerService := service.NewTimerService(timerRepo, historyRepo)

	authHandler := handler.NewAuthHandler(authService)
	timerHandler := handler.NewTimerHandler(timerService)

	engine := router.New(cfg, authHandler, timerHandler)
	log.Printf("server started on %s", cfg.Addr)
	if err := engine.Run(cfg.Addr); err != nil {
		log.Fatalf("server exited with error: %v", err)
	}
}
