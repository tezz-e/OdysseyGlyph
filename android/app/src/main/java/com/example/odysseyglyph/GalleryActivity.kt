package com.example.odysseyglyph

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GalleryActivity : AppCompatActivity() {

    private lateinit var rvPresets: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var tvVaultCount: TextView
    private lateinit var btnBack: android.widget.ImageView
    private lateinit var adapter: PresetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_gallery)

        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBarLayout, applyTop = true)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        TouchSpringAnimator.attach(btnBack)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                NavigationHelper.finishWithPop(this@GalleryActivity)
            }
        })

        rvPresets = findViewById(R.id.rvPresets)
        WindowInsetsUtil.applySystemBarsPadding(rvPresets, applyBottom = true)

        tvEmpty = findViewById(R.id.tvEmpty)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvVaultCount = findViewById(R.id.tvVaultCount)

        rvPresets.layoutManager = LinearLayoutManager(this)
        
        loadPresets()
    }

    private fun loadPresets() {
        val presets = PresetManager.getPresets(this)
        tvVaultCount.text = if (presets.isNotEmpty()) "${presets.size} presets" else ""
        if (presets.isEmpty()) {
            rvPresets.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            rvPresets.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            adapter = PresetAdapter(presets, this::onPresetClicked, this::onDeleteClicked)
            rvPresets.adapter = adapter
        }
    }

    private fun onDeleteClicked(preset: Preset) {
        showDeleteConfirmationDialog(preset) {
            PresetManager.deletePreset(this, preset.id)
            loadPresets()
            Snackbar.make(findViewById(android.R.id.content), "Preset deleted.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
        }
    }

    private fun showDeleteConfirmationDialog(preset: Preset, onConfirm: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nothing_confirm, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "DELETE PRESET"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text = "Are you sure you want to delete '${preset.name}'?\nThis action cannot be undone."

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogConfirm).setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun onPresetClicked(preset: Preset) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_preset_options, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvOptionsTitle).text = preset.name
        dialogView.findViewById<TextView>(R.id.tvOptionsBadge).text = if (preset.type == "LYRIC") {
            "Synced Lyric Animation"
        } else {
            "25×25 Matrix Preset"
        }

        val ivLargePreview = dialogView.findViewById<android.widget.ImageView>(R.id.ivLargePreview)
        var isPlaying = true
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        val binFile = java.io.File(PresetManager.getPresetsDir(this), "${preset.id}.bin")
        if (binFile.exists() && binFile.length() > 637) {
            try {
                val bytes = binFile.readBytes()
                val headerSize = if (preset.type == "LYRIC") 16 else 12
                val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val numFrames = buffer.getInt()
                val fps = buffer.getInt().let { if (it <= 0) 12 else it }
                val frameDelay = 1000L / fps

                val frames = mutableListOf<android.graphics.Bitmap>()
                var offset = headerSize

                for (i in 0 until numFrames) {
                    if (offset + 625 > bytes.size) break
                    val pixels = IntArray(625)
                    for (p in 0 until 625) {
                        val bright = bytes[offset + p].toInt() and 0xFF
                        pixels[p] = android.graphics.Color.argb(255, bright, bright, bright)
                    }
                    val bitmap = android.graphics.Bitmap.createBitmap(25, 25, android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.setPixels(pixels, 0, 25, 0, 0, 25, 25)
                    frames.add(bitmap)
                    offset += 625
                }

                if (frames.isNotEmpty()) {
                    var currentFrameIndex = 0
                    val runnable = object : Runnable {
                        override fun run() {
                            if (!isPlaying) return
                            ivLargePreview.setImageBitmap(frames[currentFrameIndex])
                            currentFrameIndex = (currentFrameIndex + 1) % frames.size
                            handler.postDelayed(this, frameDelay)
                        }
                    }
                    handler.post(runnable)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        dialog.setOnDismissListener {
            isPlaying = false
        }

        val btnSlot1 = dialogView.findViewById<MaterialButton>(R.id.btnSlot1)
        val btnSlot2 = dialogView.findViewById<MaterialButton>(R.id.btnSlot2)
        val btnSlot3 = dialogView.findViewById<MaterialButton>(R.id.btnSlot3)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btnShare)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
        btnShare.setTextColor(android.graphics.Color.WHITE)

        btnSlot1.applyLiquidGlass(pressedScale = 0.96f)
        btnSlot2.applyLiquidGlass(pressedScale = 0.96f)
        btnSlot3.applyLiquidGlass(pressedScale = 0.96f)
        btnShare.applyLiquidGlass(pressedScale = 0.96f)
        btnDelete.applyLiquidGlass(pressedScale = 0.96f)

        btnSlot1.setOnClickListener {
            assignToSlot(preset, 1)
            dialog.dismiss()
        }
        btnSlot2.setOnClickListener {
            assignToSlot(preset, 2)
            dialog.dismiss()
        }
        btnSlot3.setOnClickListener {
            assignToSlot(preset, 3)
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            sharePreset(preset)
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog(preset) {
                PresetManager.deletePreset(this, preset.id)
                loadPresets()
                dialog.dismiss()
                Snackbar.make(findViewById(android.R.id.content), "Preset deleted.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
            }
        }

        dialog.show()
    }

    private fun assignToSlot(preset: Preset, slot: Int) {
        PresetManager.assignPresetToSlot(this, preset.id, slot)
        
        val intent = Intent("com.nothing.glyph.TOY_UPDATE")
        intent.putExtra("slot", slot)
        sendBroadcast(intent)
        
        Snackbar.make(findViewById(android.R.id.content), "Assigned to Slot $slot", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
    }

    private fun sharePreset(preset: Preset) {
        val dir = PresetManager.getPresetsDir(this)
        val binFile = File(dir, "${preset.id}.bin")
        val mp3File = File(dir, "${preset.id}.mp3")

        if (!binFile.exists()) {
            Snackbar.make(findViewById(android.R.id.content), "Preset data missing.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
            return
        }

        try {
            val shareDir = File(cacheDir, "shared_presets")
            shareDir.mkdirs()
            val zipFile = File(shareDir, "${preset.name.replace(" ", "_")}.odyssey")
            
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add bin
                val binEntry = ZipEntry("frames.bin")
                zos.putNextEntry(binEntry)
                FileInputStream(binFile).use { it.copyTo(zos) }
                zos.closeEntry()
                
                // Add mp3 if exists
                if (mp3File.exists()) {
                    val mp3Entry = ZipEntry("audio.mp3")
                    zos.putNextEntry(mp3Entry)
                    FileInputStream(mp3File).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }

            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", zipFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share Preset"))
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(findViewById(android.R.id.content), "Failed to share preset.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
        }
    }
}

class PresetAdapter(
    private val presets: List<Preset>,
    private val onClick: (Preset) -> Unit,
    private val onDelete: (Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPresetName)
        val tvType: TextView = view.findViewById(R.id.tvPresetType)
        val tvDate: TextView = view.findViewById(R.id.tvPresetDate)
        val ivThumbnail: android.widget.ImageView = view.findViewById(R.id.ivThumbnail)
        val btnQuickDelete: android.widget.ImageButton = view.findViewById(R.id.btnQuickDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = presets[position]
        holder.tvName.text = p.name
        holder.tvType.text = p.type
        holder.tvDate.text = dateFormat.format(Date(p.timestamp))
        
        holder.itemView.setOnClickListener { onClick(p) }
        holder.btnQuickDelete.setOnClickListener { onDelete(p) }
        holder.itemView.applyLiquidGlass(pressedScale = 0.98f)
        holder.btnQuickDelete.applyLiquidGlass(pressedScale = 0.95f)
        
        // Load thumbnail from bin file
        val context = holder.itemView.context
        val binFile = File(PresetManager.getPresetsDir(context), "${p.id}.bin")
        if (binFile.exists() && binFile.length() > 637) {
            try {
                FileInputStream(binFile).use { fis ->
                    val headerSize = if (p.type == "LYRIC") 16 else 12
                    fis.skip(headerSize.toLong())
                    
                    val frameData = ByteArray(625)
                    val read = fis.read(frameData)
                    if (read == 625) {
                        val bitmap = android.graphics.Bitmap.createBitmap(25, 25, android.graphics.Bitmap.Config.ARGB_8888)
                        val pixels = IntArray(625)
                        for (i in 0 until 625) {
                            val bright = frameData[i].toInt() and 0xFF
                            pixels[i] = android.graphics.Color.argb(255, bright, bright, bright)
                        }
                        bitmap.setPixels(pixels, 0, 25, 0, 0, 25, 25)
                        holder.ivThumbnail.setImageBitmap(bitmap)
                    } else {
                        holder.ivThumbnail.setImageDrawable(null)
                    }
                }
            } catch (e: Exception) {
                holder.ivThumbnail.setImageDrawable(null)
            }
        } else {
            holder.ivThumbnail.setImageDrawable(null)
        }
    }

    override fun getItemCount() = presets.size
}
