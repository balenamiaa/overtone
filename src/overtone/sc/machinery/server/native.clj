(ns overtone.sc.machinery.server.native
  (:import [java.nio ByteOrder ByteBuffer CharBuffer]
           [java.nio.charset StandardCharsets])
  (:require [overtone.jna-path]
            [overtone.at-at :as at-at]
            [overtone.helpers.file :refer [get-current-directory home-dir]]
            [overtone.helpers.system :refer [get-os get-cpu-bits windows-os? os-description]]
            [overtone.sc.machinery.server.args :refer :all]
            [overtone.sc.defaults :refer [INTERNAL-POOL]]
            ;; [overtone.nativescsynth.availability :only [native-scsynth-lib-availability]]
            [clj-native.direct :refer [defclib loadlib]]
            [clj-native.structs :refer [byref]]
            [clj-native.callbacks :refer [callback]]
            [overtone.config.log :refer [warn error]]
            ))

(def native-scsynth-lib-availability
  {:windows {64 true
             32 false}
   :linux   {64 true
             32 false}
   :mac     {64 true
             32 false}})

(defn native-scsynth-available? []
  (let [os-arc-path [(get-os) (get-cpu-bits)]]
    (get-in native-scsynth-lib-availability os-arc-path)))

(declare world-options)
(declare reply-callback)
(declare sound-buffer)
(declare bool-val)

(defn fflush [_] nil)

(defonce __LOAD_SCSYNTH_NATIVE_LIB__
  (try
    (when-not (windows-os?)
      (defclib libc
        (:libname "c")
        (:functions
         (fflush fflush [void*] i32))))

    (when (native-scsynth-available?)
      (defclib lib-scsynth
        (:libname "scsynth")
        (:structs
         ;; supercollider/include/plugin_interface/SC_Rate.h
         (rate
          :sample-rate double
          :buf-rate    double
          :radians-per-sample double)

         ;; supercollider/include/server/SC_WorldOptions.h
         (world-options
          :mPassword                          constchar*
          :mNumBuffers                        i32
          :mMaxLogins                         i32
          :mMaxNodes                          i32
          :mMaxGraphDefs                      i32
          :mMaxWireBufs                       i32
          :mNumAudioBusChannels               i32
          :mNumInputBusChannels               i32
          :mNumOutputBusChannels              i32
          :mNumControlBusChannels             i32
          :mBufLength                         i32
          :mRealTimeMemorySize                i32
          :mNumSharedControls                 i32
          :mSharedControls                    float*
          :mRealTime                          byte
          :mMemoryLocking                     byte
          :mNonRealTimeCmdFilename            constchar*
          :mNonRealTimeInputFilename          constchar*
          :mNonRealTimeOutputFilename         constchar*
          :mNonRealTimeOutputHeaderFormat     constchar*
          :mNonRealTimeOutputSampleFormat     constchar*
          :mPreferredSampleRate               i32
          :mNumRGens                          i32
          :mPreferredHardwareBufferFrameSize  i32
          :mLoadGraphDefs                     i32
          :mInputStreamsEnabled               constchar*
          :mOutputStreamsEnabled              constchar*
          :mInDeviceName                      constchar*
          :mVerbosity                         i32
          :mRendezvous                        byte
          :mUGensPluginPath                   constchar*
          :mOutDeviceName                     constchar*
          :mRestrictedPath                    constchar*
          :mSharedMemoryID                    i32)

         ;; supercollider/include/plugin_interface/SC_SndBuf.h
         (sound-buffer
          :samplerate double
          :sampledur  double
          :data       float*
          :channels   i32
          :samples    i32
          :frames     i32
          :mask       i32
          :mask1      i32
          :coord      i32
          :sndfile    void*
          :isLocal    byte)

         (bool-val
          :value byte)

         ;; supercollider/include/plugin_interface/SC_World.h
         (world
          :hidden-world void*
          :interface-table void*
          :sample-rate double
          :buf-length  i32
          :buf-counter i32
          :num-audio-bus-channels   i32
          :num-control-bus-channels i32
          :num-inputs               i32
          :num-outputs              i32
          :audio-busses             float*
          :control-busses           float*
          :audio-bus-touched        i32*
          :control-bus-touched      i32*
          :num-snd-bufs             i32
          :snd-bufs                 sound-buffer*
          :snd-bufs-non-realtime    sound-buffer*
          :snd-buf-updates          void*
          :top-group                void*
          :full-rate                rate
          :buf-rate                 rate
          :num-rgens                i32
          :rgen                     void*
          :num-units                i32
          :num-graphs               i32
          :num-groups               i32
          :sample-offset            i32
          :nrt-lock                 void*
          :num-shared-controls      i32
          :shared-controls          float*
          :real-time?               byte
          :running?                 byte
          :dump-osc                 i32
          :driver-lock              void*
          :subsample-offset         float
          :verbosity                i32
          :error-notification       i32
          :local-error-notificaiton i32
          :rendezvous?              byte
          :restricted-path          constchar*)

         (reply-address
          :address      constchar*
          :protocol     i32
          :port         i32
          :socket       i32
          :reply-func   void*
          :reply-data   void*))

        (:callbacks

         ;; supercollider/include/common/SC_Reply.h
         (reply-callback [void* void* i32] void))

        ;; TODO: void* here is actually world*
        (:functions

         ;; supercollider/include/server/SC_WorldOptions.h
         (world-new World_New [world-options*] void*)
         (world-run World_WaitForQuit [void* byte*])
         (world-cleanup World_Cleanup [void* byte*])

         (world-open-udp-port World_OpenUDP [void* constchar* i32])
         (world-open-tcp-port World_OpenTCP [void* constchar* i32 i32 i32])
         (world-send-packet World_SendPacket [void* i32 byte* reply-callback] byte)
         ;; See `scsynth-get-buffer-data` for why we use `void*` instead of the
         ;; sound-buffer struct.
         (world-copy-sound-buffer World_CopySndBuf [void* i32 void* byte byte*] i32)))

      (when-not (windows-os?)
        (loadlib libc))

      (when (native-scsynth-available?)
        (loadlib lib-scsynth))

      (defonce flusher (at-at/every 500 #(fflush nil) INTERNAL-POOL :desc "Flush stdout")))
    (catch UnsatisfiedLinkError e
      (warn (with-out-str (.printStackTrace e)))
      (error "Unable to load native libs c and scsynth. Please try an external server with (use 'overtone.core)"))))

(defn flush-all
  []
  (fflush nil))

(defn set-world-options!
  [ptr option-map]
  (set! (.mPassword ptr)                         (:pwd option-map))
  (set! (.mNumBuffers ptr)                       (:max-buffers option-map))
  (set! (.mMaxLogins ptr)                        (:max-logins option-map))
  (set! (.mMaxNodes ptr)                         (:max-nodes option-map))
  (set! (.mMaxGraphDefs ptr)                     (:max-sdefs option-map))
  (set! (.mMaxWireBufs ptr)                      (:max-w-buffers option-map))
  (set! (.mNumAudioBusChannels ptr)              (:max-audio-bus option-map))
  (set! (.mNumInputBusChannels ptr)              (:max-input-bus option-map))
  (set! (.mNumOutputBusChannels ptr)             (:max-output-bus option-map))
  (set! (.mNumControlBusChannels ptr)            (:max-control-bus option-map))
  (set! (.mBufLength ptr)                        (:mBufLength option-map))
  (set! (.mRealTimeMemorySize ptr)               (:rt-mem-size option-map))
  (set! (.mNumSharedControls ptr)                (:mNumSharedControls option-map))
  (set! (.mSharedControls ptr)                   (:mSharedControls option-map))
  (set! (.mRealTime ptr)                         (:realtime? option-map))
  (set! (.mMemoryLocking ptr)                    (:mMemoryLocking option-map))
  (set! (.mNonRealTimeCmdFilename ptr)           (:mNonRealTimeCmdFilename option-map))
  (set! (.mNonRealTimeInputFilename ptr)         (:mNonRealTimeInputFilename option-map))
  (set! (.mNonRealTimeOutputFilename ptr)        (:mNonRealTimeOutputFilename option-map))
  (set! (.mNonRealTimeOutputHeaderFormat ptr)    (:mNonRealTimeOutputHeaderFormat option-map))
  (set! (.mNonRealTimeOutputSampleFormat ptr)    (:mNonRealTimeOutputSampleFormat option-map))
  (set! (.mPreferredSampleRate ptr)              (:mPreferredSampleRate option-map))
  (set! (.mNumRGens ptr)                         (:num-rand-seeds option-map))
  (set! (.mPreferredHardwareBufferFrameSize ptr) (:mPreferredHardwareBufferFrameSize option-map))
  (set! (.mLoadGraphDefs ptr)                    (:load-sdefs? option-map))
  (set! (.mInputStreamsEnabled ptr)              (:in-streams option-map))
  (set! (.mOutputStreamsEnabled ptr)             (:out-streams option-map))
  (set! (.mInDeviceName ptr)                     (:hw-device-name option-map))
  (set! (.mVerbosity ptr)                        (:verbosity option-map))
  (set! (.mRendezvous ptr)                       (:rendezvous? option-map))
  (set! (.mUGensPluginPath ptr)                  (:mUGensPluginPath option-map))
  (set! (.mOutDeviceName ptr)                    (:hw-out-device-name option-map))
  (set! (.mRestrictedPath ptr)                   (:mRestrictedPath option-map))
  (set! (.mSharedMemoryID ptr)                   (:mSharedMemoryID option-map)))

(defn scsynth
  "Load libscsynth and start the synthesis server with the given options.  Returns
  the World pointer."
  ([recv-fn] (scsynth recv-fn {}))
  ([recv-fn options-map]
   (when (not (native-scsynth-available?))
     (throw (Exception. (str "Can't connect to a native server - this version of Overtone does not yet have any compatible libraries for your system: " (os-description) ". Please consider contributing a build to the project."))))
   (let [options (byref world-options)
         cb      (callback reply-callback
                           (fn [addr msg-buf msg-size]
                             (let [byte-buf (.getByteBuffer msg-buf 0 msg-size)]
                               (recv-fn (.order byte-buf ByteOrder/BIG_ENDIAN)))))

         args    (merge-native-sc-args options-map)]
     (set-world-options! options args)
     {:world (world-new options)
      :callback cb})))

(defn scsynth-listen-udp
  [sc port]
  (world-open-udp-port (:world sc) "127.0.0.1" port))

(def SC-MAX-CONNECTIONS 1024)
(def SC-BACKLOG 64)

(defn scsynth-listen-tcp
  [sc port]
  (world-open-tcp-port (:world sc) "127.0.0.1" port SC-MAX-CONNECTIONS SC-BACKLOG))

(defn scsynth-send
  [sc ^ByteBuffer buf]
  (world-send-packet (:world sc) (.limit buf) buf (:callback sc)))

(defn scsynth-run
  "Starts the synthesis server main loop, and does not return until the /quit message
  is received."
  [sc]
  (flush-all)
  (world-run (:world sc) nil))

(defn- sound-buffer->data
  [buf]
  (.getPointer (.getPointer buf) 0x10))

(defn scsynth-get-buffer-data
  "Get a an array of floats for the synthesis sound buffer with the given ID."
  [sc buf-id]
  (let [buf (byref sound-buffer)
        #_ #_changed? (byref bool-val)  ; This gives us an exception.
        changed? (java.nio.ByteBuffer/allocate 1)]
    ;; We use a `buf` pointer here as using `buf` directly in the `sound-buffer` struct
    ;; type throws an exception (the same exception occurs when trying to do
    ;; `(.readField buf "data")`).
    (world-copy-sound-buffer (:world sc) buf-id (.getPointer buf) 0 changed?)
    ;; Calling `(.data buf)` or `(.samples buf)` is not working, so we
    ;; access the buffer using lower level operations.
    (.getFloatArray (sound-buffer->data buf) 0 (.readField buf "samples"))))
