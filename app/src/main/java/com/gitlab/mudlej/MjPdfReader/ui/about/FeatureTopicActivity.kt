// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.about

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.gitlab.mudlej.MjPdfReader.core.ui.setupScreenChrome
import com.gitlab.mudlej.MjPdfReader.databinding.ActivityFeatureTopicBinding
import com.gitlab.mudlej.MjPdfReader.databinding.FeatureRowItemBinding

class FeatureTopicActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureTopicBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureTopicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupScreenChrome()

        val topicName = intent.getStringExtra(EXTRA_TOPIC)
        val topic = FeatureTopic.entries.find { it.name == topicName }
        if (topic == null) {
            finish()
            return
        }
        setTitle(topic.titleRes)
        bindEntries(topic)
    }

    private fun bindEntries(topic: FeatureTopic) {
        for (entry in topic.entries) {
            val row = FeatureRowItemBinding.inflate(layoutInflater, binding.entriesContainer, true)
            row.rowTitle.setText(entry.titleRes)
            row.rowBody.setText(entry.bodyRes)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TOPIC = "topic"
    }
}
