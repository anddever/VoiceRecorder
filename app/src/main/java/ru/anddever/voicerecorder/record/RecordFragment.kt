package ru.anddever.voicerecorder.record

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.fragment_record.*
import ru.anddever.voicerecorder.MainActivity
import ru.anddever.voicerecorder.R
import ru.anddever.voicerecorder.database.RecordDatabase
import ru.anddever.voicerecorder.database.RecordDatabaseDao
import ru.anddever.voicerecorder.databinding.FragmentRecordBinding
import java.io.File

private const val MY_PERMISSIONS_RECORD_AUDIO = 123

class RecordFragment : Fragment() {

    private lateinit var viewModel: RecordViewModel
    private lateinit var mainActivity: MainActivity
    private var count: Int? = null
    private var database: RecordDatabaseDao? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DataBindingUtil.inflate<FragmentRecordBinding>(
            inflater,
            R.layout.fragment_record,
            container,
            false
        )

        database = context?.let { RecordDatabase.getInstance(it).recordDatabaseDao }

        mainActivity = activity as MainActivity

        viewModel = ViewModelProvider(this).get(RecordViewModel::class.java)

        binding.recordViewModel = viewModel
        binding.lifecycleOwner = this.viewLifecycleOwner

        if (!mainActivity.isServiceRunning()) {
            viewModel.resetTimer()
        } else {
            binding.playButton.setImageResource(R.drawable.ic_baseline_stop_24)
        }

        binding.playButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.RECORD_AUDIO), MY_PERMISSIONS_RECORD_AUDIO
                )
            } else {
                if (mainActivity.isServiceRunning()) {
                    onRecord(false)
                    viewModel.startTimer()
                } else {
                    onRecord(true)
                    viewModel.startTimer()
                }
            }
        }

        createChannel(
            getString(R.string.notification_channel_id),
            getString(R.string.notification_channel_name)
        )

        return binding.root
    }

    private fun onRecord(start: Boolean) {
        val intent = Intent(activity, RecordService::class.java)

        if (start) {
            playButton.setImageResource(R.drawable.ic_baseline_stop_24)
            Toast.makeText(activity, R.string.toast_recording_start, Toast.LENGTH_SHORT).show()

            val folder = File(
                activity?.getExternalFilesDir(null)
                    ?.absolutePath.toString() + "/VoiceRecorder"
            )
            if (!folder.exists()) {
                folder.mkdir()
            }

            activity?.startService(intent)
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            playButton.setImageResource(R.drawable.ic_baseline_mic_white_36)

            activity?.stopService(intent)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    onRecord(true)
                    viewModel.startTimer()
                } else {
                    Toast.makeText(
                        activity, getString(R.string.toast_recording_permissions),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun createChannel(channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
                .apply {
                    setShowBadge(false)
                    setSound(null, null)
                }
            val notificationManager = requireActivity().getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}