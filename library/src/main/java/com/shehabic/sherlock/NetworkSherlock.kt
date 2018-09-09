package com.shehabic.sherlock

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.shehabic.sherlock.db.Db
import com.shehabic.sherlock.db.DbWorkerThread
import com.shehabic.sherlock.db.NetworkRequests
import com.shehabic.sherlock.db.Sessions
import com.shehabic.sherlock.ui.NetworkSherlockAnchor

interface SherlockOnSuccessListener<T> {
    fun onSuccess(requests: T)
}

class NetworkSherlock private constructor(private val config: Config) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: NetworkSherlock? = null

        fun getInstance(): NetworkSherlock {
            return INSTANCE ?: getInstance(Config())
        }

        fun getInstance(config: Config): NetworkSherlock {
            if (INSTANCE == null) {
                INSTANCE = NetworkSherlock(config)
            }

            return INSTANCE!!
        }
    }

    private var captureRequests: Boolean = true
    private var uiAnchor: NetworkSherlockAnchor = NetworkSherlockAnchor.getInstance()
    private var appContext: Context? = null
    private var sessionId: Int? = null
    private var dbWorkerThread: DbWorkerThread? = null
    private val activityCycleCallbacks = NetworkSherlock.NetworkSherlockLifecycleHandler()

    data class Config(
        val showAnchor: Boolean = true,
        val showNetworkActivity: Boolean = true
    )

    class NetworkSherlockLifecycleHandler : Application.ActivityLifecycleCallbacks {
        override fun onActivityPaused(activity: Activity?) {
            NetworkSherlock.getInstance().onActivityPaused(activity)
        }

        override fun onActivityResumed(activity: Activity?) {
            NetworkSherlock.getInstance().onActivityResumed(activity)
        }

        override fun onActivityStarted(activity: Activity?) {
        }

        override fun onActivityDestroyed(activity: Activity?) {
        }

        override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
        }

        override fun onActivityStopped(activity: Activity?) {
        }

        override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        }
    }

    fun onActivityResumed(activity: Activity?) {
        if (config.showAnchor && isNonLibScreen(activity)) {
            uiAnchor.addUI(activity)
        }
    }

    fun onActivityPaused(activity: Activity?) {
        if (config.showAnchor && isNonLibScreen(activity)) {
            uiAnchor.removeUI(activity)
        }
    }

    private fun isNonLibScreen(activity: Activity?): Boolean {
        return !activity!!::class.java.canonicalName.contains(BuildConfig.APPLICATION_ID)
    }


    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
            activityCycleCallbacks
        )
        dbWorkerThread = DbWorkerThread("dbWorkerThread")
        dbWorkerThread?.start()
        dbWorkerThread?.prepareHandler()
        startNewSession()
    }

    private fun getDb(): Db {
        validateInitialization()
        return Db.getInstance(appContext!!)!!
    }

    private fun initSessionIfNeeded() {
        if (sessionId == null) {
            startNewSession()
        }
    }

    fun startNewSession() {
        validateInitialization()
        dbWorkerThread?.postTask(Runnable {
            val session = Sessions(startedAt = System.currentTimeMillis())
            sessionId = getDb().networkRequestsDao().insertSession(session).toInt()
        })
    }

    private fun validateInitialization() {
        if (appContext == null) {
            throw RuntimeException("NetworkSherlock not initialized")
        }
    }

    fun startRequest() {
        if (config.showNetworkActivity) {
            uiAnchor.onRequestStarted()
        }
    }

    fun endRequest() {
        if (config.showNetworkActivity) {
            uiAnchor.onRequestEnded()
        }
    }

    fun addRequest(request: NetworkRequests) {
        if (!captureRequests) return
        validateInitialization()
        request.sessionId = sessionId!!
        getDb().networkRequestsDao().insertRequest(request)
    }

    fun getCurrentRequests(listener: SherlockOnSuccessListener<List<NetworkRequests>>) {
        validateInitialization()
        dbWorkerThread?.postTask(Runnable {
            listener.onSuccess(getDb().networkRequestsDao().getAllRequestsForSession(sessionId!!))
        })
    }

    fun getCurrentRequestsSync(): List<NetworkRequests> {
        validateInitialization()
        val items = getDb().networkRequestsDao().getAllRequestsForSession(sessionId!!)

        return items
    }

    fun clearAll() {
        getDb().networkRequestsDao().getAllRequests()
        getDb().networkRequestsDao().deleteAllSessions()
    }

    fun getSessionId(): Int {
        return sessionId ?: 0
    }

    fun pauseRecording() {
        captureRequests = false
    }

    fun resumeRecording() {
        captureRequests = true
    }

    fun isRecording(): Boolean {
        return captureRequests
    }

    fun destroy() {
        validateInitialization()
        (appContext as Application).unregisterActivityLifecycleCallbacks(activityCycleCallbacks)
        appContext = null
    }
}