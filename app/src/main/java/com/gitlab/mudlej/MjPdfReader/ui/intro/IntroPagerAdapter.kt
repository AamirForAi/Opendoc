// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.intro

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.gitlab.mudlej.MjPdfReader.databinding.IntroFeatureRowItemBinding
import com.gitlab.mudlej.MjPdfReader.databinding.IntroPageItemBinding
import com.google.android.material.color.MaterialColors

class IntroPage(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    val features: List<IntroFeature>,
    val isLogo: Boolean = false,
    @StringRes val footerHtmlRes: Int = 0,
)

class IntroFeature(
    @DrawableRes val iconRes: Int,
    @StringRes val textRes: Int,
)

class IntroPagerAdapter(
    private val pages: List<IntroPage>,
) : RecyclerView.Adapter<IntroPagerAdapter.PageViewHolder>() {

    class PageViewHolder(val binding: IntroPageItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return PageViewHolder(IntroPageItemBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        bindIcon(holder.binding, page)
        holder.binding.pageTitle.setText(page.titleRes)
        bindFeatures(holder.binding, page)
        bindFooter(holder.binding, page)
    }

    private fun bindIcon(binding: IntroPageItemBinding, page: IntroPage) {
        val icon = binding.pageIcon
        val card = binding.iconCard
        icon.setImageResource(page.iconRes)
        val iconSize: Int
        if (page.isLogo) {
            card.setCardBackgroundColor(Color.TRANSPARENT)
            icon.imageTintList = null
            iconSize = card.layoutParams.width
        } else {
            card.setCardBackgroundColor(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorSecondaryContainer)
            )
            icon.imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(icon, com.google.android.material.R.attr.colorOnSecondaryContainer)
            )
            iconSize = card.layoutParams.width / 2
        }
        icon.layoutParams = icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
    }

    private fun bindFeatures(binding: IntroPageItemBinding, page: IntroPage) {
        val featuresLayout = binding.featuresLayout
        featuresLayout.removeAllViews()
        val inflater = LayoutInflater.from(featuresLayout.context)
        for (feature in page.features) {
            val row = IntroFeatureRowItemBinding.inflate(inflater, featuresLayout, true)
            row.featureText.setText(feature.textRes)
            row.featureIcon.setImageResource(feature.iconRes)
        }
    }

    private fun bindFooter(binding: IntroPageItemBinding, page: IntroPage) {
        val footer = binding.footerText
        if (page.footerHtmlRes == 0) {
            footer.visibility = View.GONE
            return
        }
        footer.visibility = View.VISIBLE
        val html = footer.context.getString(page.footerHtmlRes)
        footer.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        footer.movementMethod = LinkMovementMethod.getInstance()
    }
}
