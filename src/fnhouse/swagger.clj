(ns fnhouse.swagger
  "Swagger documentation"
  (:use plumbing.core)
  (:require
    [fnhouse.handlers :as handlers]
    [ring.swagger.core :as ring-swagger]
    [clojure.set :refer [map-invert]]
    [schema.core :as s]
    [ring.middleware.resource :as resource]))

(defn- generate-nickname [annotated-handler]
  (str (:api annotated-handler) (get-in annotated-handler [:info :source-map :name])))

(defn- convert-parameters [request]
  (for [[type f] {:body :body, :query :query-params, :path :uri-args}]
    {:type type :model (f request)}))

(defn collect-route [ns-sym->prefix api-routes annotated-handler]
  (letk [[[:info method path description request responses
           [:source-map ns]]] annotated-handler]
    (let [prefix (ns-sym->prefix (symbol ns))]
      (update-in api-routes [prefix]
        update-in [:routes]
        conj {:method method
              :uri path
              :metadata {:summary description
                         :return (get responses 200)
                         :nickname (generate-nickname annotated-handler)
                         :parameters (convert-parameters request)}}))))

(defn collect-resource-meta [api-routes [ns-sym prefix]]
  (letk [[{doc nil}] (meta (the-ns ns-sym))]
    (update-in api-routes [prefix]
      assoc :description doc)))

(defn collect-routes [handlers prefix->ns-sym]
  (let [ns-sym->prefix (map-invert prefix->ns-sym)
        api-routes (reduce (partial collect-route ns-sym->prefix) {} handlers)]
    (reduce collect-resource-meta api-routes ns-sym->prefix)))

(defn swagger-ui [handler]
  (resource/wrap-resource handler "swagger-ui"))

(defnk $api-docs$GET
  "Apidocs"
  {:responses {200 s/Any}}
  [[:resources swagger]]
  (ring-swagger/api-listing {} swagger))

(defnk $api-docs$:resource$GET
  "Apidoc"
  {:responses {200 s/Any}}
  [[:request [:uri-args resource :- String] :as request]
   [:resources swagger]]
  (ring-swagger/api-declaration {} swagger resource
    (ring-swagger/basepath request)))
