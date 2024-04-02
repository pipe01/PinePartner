package main

import (
	"flag"
	"io"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

func main() {
	addr := flag.String("addr", ":80", "address to bind http server to")
	flag.Parse()

	token := os.Getenv("GITHUB_TOKEN")

	var cachedResp []byte
	var cachedTime time.Time
	var cacheLock sync.Mutex

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		cacheLock.Lock()
		defer cacheLock.Unlock()

		if cachedResp == nil || time.Since(cachedTime) > 1*time.Hour {
			log.Println("Cache miss")

			req, _ := http.NewRequest("GET", "https://api.github.com/repos/InfiniTimeOrg/InfiniTime/releases", nil)

			if token != "" {
				req.Header.Set("Authorization", "Bearer "+token)
			}

			resp, err := http.DefaultClient.Do(req)
			if err != nil {
				log.Printf("Failed to fetch data from GitHub: %v", err)
				http.Error(w, "Failed to fetch data from GitHub", http.StatusInternalServerError)
				return
			}
			defer resp.Body.Close()

			respBytes, err := io.ReadAll(resp.Body)
			if err != nil {
				log.Printf("Failed to read response body: %v", err)
				http.Error(w, "Failed to read response body", http.StatusInternalServerError)
				return
			}

			cachedResp = respBytes
			cachedTime = time.Now()
		} else {
			log.Println("Cache hit")
		}

		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Write([]byte(cachedResp))
	})

	log.Printf("Listening on %s", *addr)

	if err := http.ListenAndServe(*addr, nil); err != nil {
		log.Fatalf("Failed to start http server: %v", err)
	}
}
