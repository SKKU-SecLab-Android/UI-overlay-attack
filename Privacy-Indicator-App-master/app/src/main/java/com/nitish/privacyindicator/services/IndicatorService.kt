package com.nitish.privacyindicator.services

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.AvailabilityCallback
import android.location.GnssStatus
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioManager.AudioRecordingCallback
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nitish.privacyindicator.BuildConfig
import com.nitish.privacyindicator.R
import com.nitish.privacyindicator.databinding.IndicatorsLayoutBinding
import com.nitish.privacyindicator.db.AccessLogsDatabase
import com.nitish.privacyindicator.helpers.setViewTint
import com.nitish.privacyindicator.helpers.updateOpacity
import com.nitish.privacyindicator.helpers.updateSize
import com.nitish.privacyindicator.models.AccessLog
import com.nitish.privacyindicator.models.IndicatorType
import com.nitish.privacyindicator.repository.AccessLogsRepo
import com.nitish.privacyindicator.repository.SharedPrefManager
import com.nitish.privacyindicator.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.util.Log

class IndicatorService : AccessibilityService() {
    private lateinit var binding: IndicatorsLayoutBinding
    private var cameraManager: CameraManager? = null
    private var cameraCallback: AvailabilityCallback? = null
    private var locationManager: LocationManager? = null
    private var locationCallback: GnssStatus.Callback? = null
    private var audioManager: AudioManager? = null
    private var micCallback: AudioRecordingCallback? = null
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var windowManager: WindowManager
    private lateinit var accessLogsRepo: AccessLogsRepo
    private val notification_channel_id = "PRIVACY_INDICATORS_NOTIFICATION"
    private var notifManager: NotificationManagerCompat? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private val notificationID = 256
    private val IS_ROUNDED_CONERS_OVERLAY = 0x1500010
    private val PRIVATE_FLAG_FORCE_DECOR_VIEW_VISIBILITY = 16384
    private var isCameraOn = false
    private var isMicOn = false
    private var isLocationOn = false
    private var currentAppId = BuildConfig.APPLICATION_ID


    override fun onCreate() {
        Log.e("yurak", "onCreate")
        super.onCreate()
        fetchData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("yurak", "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        Log.e("yurak", "onServiceConnected")
        createOverlay()
        setUpInnerViews()
        startCallBacks()
    }

    private fun fetchData() {
        Log.e("yurak", "fetchData")
        sharedPrefManager = SharedPrefManager.getInstance(applicationContext)
        accessLogsRepo = AccessLogsRepo(AccessLogsDatabase(this))
    }

    private fun startCallBacks() {
        Log.e("yurak", "startCallBacks")
        if (cameraManager == null) cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraManager!!.registerAvailabilityCallback(getCameraCallback(), null)

        if (audioManager == null) audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager!!.registerAudioRecordingCallback(getMicCallback(), null)

        registerLocationCallback()
    }

    //This feature is EXPERIMENTAL
    private fun registerLocationCallback() {
        /*if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(locationManager==null) locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager!!.registerGnssStatusCallback(getLocationCallback())
            val locationListener = LocationListener {  }
            locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 8.4f,locationListener)
            locationManager!!.removeUpdates(locationListener)
        }else{
            sharedPrefManager.isLocationEnabled = false
        }*/
    }

    private fun getCameraCallback(): AvailabilityCallback {
        cameraCallback = object : AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                Log.e("yurak", "onCameraAvailable")
                super.onCameraAvailable(cameraId)
                isCameraOn = false
                hideCam()
                dismissNotification()
            }

            override fun onCameraUnavailable(cameraId: String) {
                Log.e("yurak", "onCameraUnavailable")
                super.onCameraUnavailable(cameraId)
                isCameraOn = true
                showCam()
                triggerVibration()
                showNotification()
            }
        }
        return cameraCallback as AvailabilityCallback
    }

    private fun getMicCallback(): AudioRecordingCallback {
        micCallback = object : AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                if (configs.size > 0) {
                    isMicOn = true
                    showMic()
                    triggerVibration()
                    showNotification()
                } else {
                    isMicOn = false
                    hideMic()
                    dismissNotification()
                }
            }
        }
        return micCallback as AudioRecordingCallback
    }

    private fun getLocationCallback(): GnssStatus.Callback {
        locationCallback = object : GnssStatus.Callback() {
            override fun onStarted() {
                super.onStarted()
                isLocationOn = true
                showLocation()
                triggerVibration()
            }

            override fun onStopped() {
                super.onStopped()
                isLocationOn = false
                hideLocation()
            }
        }
        return locationCallback as GnssStatus.Callback
    }

    private fun triggerVibration() {
        if (sharedPrefManager.isVibrationEnabled) {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                //deprecated in API 26
                v.vibrate(500)
            }
        }
    }

    private fun setUpInnerViews() {
        Log.e("yurak", "setUpInnerViews")
        setViewColors()
        showInitialAnimation(true)
    }

    private fun showInitialAnimation(isEnabled: Boolean) {
        Log.e("yurak", "showInitialAnimation")
        val delay = if (isEnabled) 1000 else 0
        binding.ivLoc.postDelayed({
            binding.ivLoc.visibility = View.GONE
            binding.ivCam.visibility = View.GONE
            binding.ivMic.visibility = View.GONE
        }, delay.toLong())
    }

    private fun createOverlay() {
        Log.e("yurak", "createOverlay")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = WindowManager.LayoutParams()
        layoutParams.apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            // TYPE_TOAST
            // TYPE_APPLICATION_OVERLAY
            //format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    //WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            //privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_IS_ ROUNDED CORNERS OVERLAY
            gravity = layoutGravity
        }
        //layoutParams.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_IS_ ROUNDED CORNERS OVERLAY
        binding = IndicatorsLayoutBinding.inflate(LayoutInflater.from(this))
        windowManager.addView(binding.root, layoutParams)
    }

    private fun updateLayoutGravity() {
        Log.e("yurak", "updateLayoutGravity")
        layoutParams.gravity = layoutGravity
        windowManager.updateViewLayout(binding.root, layoutParams)
    }

    private val layoutGravity: Int
        get() = sharedPrefManager.indicatorPosition.layoutGravity

    private fun makeLog(indicatorType: IndicatorType) {
        Log.e("yurak", "makeLog")
        if (isLogEligible(currentAppId)) {
            val log = AccessLog(System.currentTimeMillis(), currentAppId, getAppName(currentAppId), indicatorType)
            GlobalScope.launch(Dispatchers.IO) {
                accessLogsRepo.save(log)
            }
        }
    }

    private fun getAppName(packageName: String): String {

        val packageManager = applicationContext.packageManager
        Log.e("yurak", "getAppName" + packageManager)
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)) as String
        } catch (exp: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isLogEligible(currentAppId: String): Boolean {
        return currentAppId != BuildConfig.APPLICATION_ID
    }

    private fun showMic() {
        Log.e("yurak", "showMic")
        if (sharedPrefManager.isMicIndicatorEnabled) {
            updateIndicatorProperties()
            binding.ivMic.visibility = View.VISIBLE
            makeLog(IndicatorType.MICROPHONE)
        }
    }

    private fun hideMic() {
        Log.e("yurak", "hideMic")
        binding.ivMic.visibility = View.GONE
    }

    private fun showCam() {
        Log.e("yurak", "showCam")
        if (sharedPrefManager.isCameraIndicatorEnabled) {
            updateIndicatorProperties()
            binding.ivCam.visibility = View.VISIBLE
            makeLog(IndicatorType.CAMERA)
        }
    }

    private fun hideCam() {
        Log.e("yurak", "hideCam")
        binding.ivCam.visibility = View.GONE
    }

    private fun showLocation() {
        Log.e("yurak", "showLocation")
        if (sharedPrefManager.isLocationEnabled) {
            updateIndicatorProperties()
            binding.ivLoc.visibility = View.VISIBLE
            makeLog(IndicatorType.LOCATION)
        }
    }

    private fun hideLocation() {
        Log.e("yurak", "hideLocation")
        binding.ivLoc.visibility = View.GONE
    }

    private fun updateIndicatorProperties() {
        Log.e("yurak", "updateIndicatorProperties")
        updateLayoutGravity()
        //updateIndicatorsSize()
        //updateIndicatorsOpacity()
        setViewColors()
    }

    private fun setViewColors() {
        Log.e("yurak", "setViewColors")
        val dotsTint = sharedPrefManager.indicatorColor
        val indicatorBackground = sharedPrefManager.indicatorBackgroundColor
        binding.ivCam.setViewTint(dotsTint)
        binding.ivMic.setViewTint(dotsTint)
        binding.ivLoc.setViewTint(dotsTint)
        binding.llBackground.setBackgroundColor(Color.parseColor(indicatorBackground))
    }

    private fun updateIndicatorsOpacity() {
        Log.e("yurak", "updateIndicatorsOpacity")
        binding.root.updateOpacity(sharedPrefManager.indicatorOpacity.opacity)
    }

    private fun updateIndicatorsSize() {
        Log.e("yurak", "updateIndicatorsSize")
        val size = sharedPrefManager.indicatorSize.size
        binding.cvIndicators.radius = (size / 2).toFloat()
        binding.ivCam.updateSize(size)
        binding.ivMic.updateSize(size)
        binding.ivLoc.updateSize(size)
    }

    fun upScaleView(view: View) {
        Log.e("yurak", "upScaleView")
        val fade_in = ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        fade_in.duration = 350
        fade_in.fillAfter = true
        view.startAnimation(fade_in)
    }

    fun downScaleView(view: View) {
        Log.e("yurak", "downScaleView")
        val fade_in = ScaleAnimation(1f, 0f, 1f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        fade_in.duration = 350
        fade_in.fillAfter = true
        view.startAnimation(fade_in)
    }

    private fun setupNotification() {
        Log.e("yurak", "setupNotification")
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(applicationContext, notification_channel_id)
                .setSmallIcon(R.drawable.camera_indicator2)
                .setContentTitle(notificationTitle)
                .setContentText(notificationDescription)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        notifManager = NotificationManagerCompat.from(applicationContext)
    }

    private val notificationTitle: String
        get() {
            if (isCameraOn && isMicOn) return "Your Camera and Mic is ON"
            if (isCameraOn && !isMicOn) return "Your Camera is ON"
            return if (!isCameraOn && isMicOn) "Your MIC is ON" else "Your Camera or Mic is ON"
        }
    private val notificationDescription: String
        get() {
            if (isCameraOn && isMicOn) return "A third-party app is using your Camera and Microphone"
            if (isCameraOn && !isMicOn) return "A third-party app is using your Camera"
            return if (!isCameraOn && isMicOn) "A third-party app is using your Microphone" else "A third-party app is using your Camera or Microphone"
        }

    private fun showNotification() {
        Log.e("yurak", "showNotification")
        if (sharedPrefManager.isNotificationEnabled) {
            setupNotification()
            if (notifManager != null) notifManager!!.notify(notificationID, notificationBuilder!!.build())
        }
    }

    private fun dismissNotification() {
        Log.e("yurak", "dismissNotification")
        if (isCameraOn || isMicOn) {
            showNotification()
        } else {
            if (notifManager != null) notifManager!!.cancel(notificationID)
        }
    }

    private val pendingIntent: PendingIntent
        get() {
            val intent = Intent(applicationContext, HomeActivity::class.java)
            return PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun createNotificationChannel() {
        Log.e("yurak", "createNotificationChannel")
        val notificationChannel = "Notifications for Privacy Indicators"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(notification_channel_id, notificationChannel, importance)
            val description = getString(R.string.notification_alert_summary)
            channel.description = description
            channel.lightColor = Color.RED
            val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {}
    override fun onAccessibilityEvent(accessibilityEvent: AccessibilityEvent) {
        Log.e("yurak", "onAccessibilityEvent")
        if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && accessibilityEvent.packageName != null) {
            currentAppId = try {
                val componentName = ComponentName(accessibilityEvent.packageName.toString(), accessibilityEvent.className.toString())
                componentName.packageName
            } catch (exp: Exception) {
                BuildConfig.APPLICATION_ID
            }
        }
    }

    private fun unRegisterCameraCallBack() {
        Log.e("yurak", "unRegisterCameraCallBack")
        if (cameraManager != null
                && cameraCallback != null) {
            cameraManager!!.unregisterAvailabilityCallback(cameraCallback!!)
        }
    }

    private fun unRegisterMicCallback() {
        Log.e("yurak", "unRegisterMicCallback")
        if (audioManager != null
                && micCallback != null) {
            audioManager!!.unregisterAudioRecordingCallback(micCallback!!)
        }
    }

    private fun unRegisterLocationCallback() {
        Log.e("yurak", "unRegisterLocationCallback")
        if (locationManager != null
                && locationCallback != null) {
            locationManager!!.unregisterGnssStatusCallback(locationCallback!!)
        }
    }

    override fun onDestroy() {
        Log.e("yurak", "onDestroy")
        unRegisterCameraCallBack()
        unRegisterMicCallback()
        unRegisterLocationCallback()
        super.onDestroy()
    }
}