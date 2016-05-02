(ns webserver.core
  (:require [webserver.handler :refer [my-app init-mem-db]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn -main [& args]
  (init-mem-db)
  (run-jetty my-app {:port 8080}))

(comment
  (run-jetty #'my-app {:port 8080 :join? false})
  (my-app {:request-method :post :uri "/login/" :body "username=admin&password=1234"})
  (my-app {:request-method :get :uri "/admin/"
           :headers {"cookie" "ring-session=5c39d06a-156d-401f-ae1c-f86a2ca717d6"}}))
