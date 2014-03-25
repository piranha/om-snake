(ns om-snake.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [clojure.string :as s]
            [cljs.core.async :refer [chan timeout put! >! <!]]))

(enable-console-print!)

;; utils

(defn cx [& {:as classes}]
  (s/join " " (for [[k v] classes
                    :when v]
                (name k))))



;; state

(def KEYS {37 :left 38 :up 39 :right 40 :down})

(let [size 15]
  (def app-state (atom {:size size
                        :snake [(quot (* size size) 2)]
                        :fruit 17
                        :dir :up
                        :dead false})))

;; logic

(defn next-head [head dir size]
  (+ head (dir {:up (- size)
                :down size
                :left -1
                :right 1})))

(defn rand-cell [size]
  (rand-int (* size size)))

(defn spawn-fruit [size snake]
  (loop [cell (rand-cell size)]
    (if (some #{cell} snake)
      (recur (rand-cell size))
      cell)))

(defn find-border [head size]
  {:up (< head size)
   :down (= (dec size) (quot head size))
   :left (= 0 (mod head size))
   :right (= (dec size) (mod head size))})

(defn next-tick [{:keys [snake dir fruit size] :as state}]
  (let [head (first snake)
        new (next-head head dir size)
        snake-tail (if (= new fruit)
                     snake
                     (butlast snake))
        snake (into [new] snake-tail)
        fruit (if (= new fruit)
                (spawn-fruit size snake)
                fruit)
        borders (find-border head size)]
    (if (or (dir borders)
            (some #{new} snake-tail))
      (assoc state :dead true)
      (assoc state
        :snake snake
        :fruit fruit))))

(defn handle-key [key state]
  (assoc state :dir key))

;; html

(defn field [{:keys [size snake fruit] :as app} owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:table
        (for [i (range size)]
          [:tr
           (for [j (range size)
                 :let [id (+ j (* i size))]]
             [:td
              {:class (cx :snake (some #{id} snake)
                          :fruit (= fruit id))}
              " "])])]))))

(defn root [P owner]
  (reify
    om/IDidMount
    (did-mount [this]
      (let [tick 200
            control (chan)]
        (go (loop [ticker (timeout tick)]
              (alt!
               ticker ([e c]
                         (when-not (:dead @P)
                           (om/transact! P next-tick)
                           (recur (timeout tick))))
               control ([e c]
                          (om/transact! P (partial handle-key e))
                          (recur ticker)))))
        (js/addEventListener
         "keydown"
         (fn [e]
           (if-let [k (KEYS (.-keyCode e))]
             (put! control k))))))

    om/IRender
    (render [this]
      (html
       [:div
        [:h1 "Snake"]
        [:div (if (:dead P)
                "you're dead"
                (str "Direction: " (:dir P) ", length: " (count (:snake P))))]
        (om/build field P)]))))

(om/root root app-state {:target (. js/document (getElementById "app"))})
