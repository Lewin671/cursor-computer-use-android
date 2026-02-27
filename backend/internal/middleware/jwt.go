package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

const ContextUserIDKey = "userID"

func JWTAuth(secret string) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    "missing_token",
				"message": "authorization token is missing",
			})
			c.Abort()
			return
		}
		rawToken := strings.TrimSpace(parts[1])
		claims := jwt.MapClaims{}
		token, err := jwt.ParseWithClaims(rawToken, claims, func(token *jwt.Token) (interface{}, error) {
			return []byte(secret), nil
		})
		if err != nil || !token.Valid {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    "invalid_token",
				"message": "authorization token is invalid",
			})
			c.Abort()
			return
		}
		if claims["token_type"] != "access" {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    "invalid_token",
				"message": "token type is not access",
			})
			c.Abort()
			return
		}
		userID, _ := claims["sub"].(string)
		if userID == "" {
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    "invalid_token",
				"message": "token subject is missing",
			})
			c.Abort()
			return
		}
		c.Set(ContextUserIDKey, userID)
		c.Next()
	}
}
