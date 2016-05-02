(ns webserver.handler
  (:require [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers :as hashers]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes context GET POST]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [response redirect]]
            [ring.util.http-response :refer [content-type ok]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

;; "Database"

(defn uuid [] (java.util.UUID/randomUUID))
(def userstore (atom {}))

(defn create-user! [user]
  (let [password (:password user)
        user-id (uuid)]
    (-> user
        (assoc :id user-id :password-hash (hashers/encrypt password))
        (dissoc :password)
        (->> (swap! userstore assoc user-id)))))

(defn get-user [user-id]
  (get @userstore user-id))

(defn get-user-by-username-and-password [username password]
  (prn username password)
  (reduce (fn [_ user]
            (if (and (= (:username user) username)
                     (hashers/check password (:password-hash user)))
              (reduced user))) (vals @userstore)))

;; Handler functions

(defn get-index [req]
  (slurp (io/resource "public/index.html")))

(defn get-admin [req]
  (slurp (io/resource "public/admin.html")))

(defn get-login [req]
  (slurp (io/resource "public/login.html")))

(defn post-login [{{username "username" password "password"} :form-params
                   session :session :as req}]
  (if-let [user (get-user-by-username-and-password username password)]
    ; If authenticated
    (assoc (redirect "/")
           :session (assoc session :identity (:id user)))
    ; Otherwise
    (redirect "/login/")))

(defn post-logout [{session :session}]
  (assoc (redirect "/login/")
         :session (dissoc session :identity)))

;; Auth

(defn is-authenticated [{user :user :as req}]
  (not (nil? user)))

(defn not-authorized [req &val]
  (ok "not authorized"))

(defn wrap-user [handler]
  (fn [{user-id :identity :as req}]
    (handler (assoc req :user (get-user user-id)))))

(defn init-mem-db []
  (create-user! {:username "admin" :password "1234"})  )

;; Routes

(defroutes admin-routes
  (GET "/" [] get-admin))

(defroutes all-routes
  (context "/admin" []
     (restrict admin-routes {:handler is-authenticated
                             :on-error not-authorized}))
  (GET "/" [] get-index)
  (GET "/login/" [] get-login)
  (POST "/login/" [] post-login)
  (POST "/logout/" [] post-logout))

(def backend (session-backend))
(def my-app
  (-> #'all-routes
      (wrap-user)
      (wrap-authentication backend)
      (wrap-authorization backend)
      (wrap-session)
      (wrap-params)))


