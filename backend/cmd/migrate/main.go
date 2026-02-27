package main

import (
	"log"

	"pomodoro-sync/backend/internal/config"
	"pomodoro-sync/backend/internal/db"
)

func main() {
	cfg := config.Load()
	dbConn, err := db.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("open db failed: %v", err)
	}
	defer dbConn.Close()
	if err := db.RunMigrations(dbConn, "migrations"); err != nil {
		log.Fatalf("migrate failed: %v", err)
	}
	log.Printf("migrations applied successfully")
}
