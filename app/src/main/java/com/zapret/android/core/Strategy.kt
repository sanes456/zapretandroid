package com.zapret.android.core

import androidx.annotation.StringRes
import com.zapret.android.R

/**
 * A DPI bypass strategy expressed as a set of ciadpi arguments.
 *
 * Recipes follow the official byedpi README recommendations adapted for the
 * Linux kernel (Android): --disorder relies on TTL=1 trick + SACK retransmit,
 * --fake should not reach the server, therefore --md5sig is available but not
 * guaranteed on all devices.
 */
data class Strategy(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val args: String,
)

object Strategies {

    const val ID_NONE = "none"

    val ALL: List<Strategy> = listOf(
        Strategy(
            id = "auto",
            titleRes = R.string.strategy_auto,
            descriptionRes = R.string.strategy_auto_desc,
            args = "--disorder 1 --auto=torst --timeout 3 --tlsrec 1+s",
        ),
        Strategy(
            id = "disorder",
            titleRes = R.string.strategy_disorder,
            descriptionRes = R.string.strategy_disorder_desc,
            args = "--disorder 1",
        ),
        Strategy(
            id = "split_disorder",
            titleRes = R.string.strategy_split_disorder,
            descriptionRes = R.string.strategy_split_disorder_desc,
            args = "--split 1+s --disorder 3+s",
        ),
        Strategy(
            id = "fake",
            titleRes = R.string.strategy_fake,
            descriptionRes = R.string.strategy_fake_desc,
            args = "--fake -1 --ttl 8",
        ),
        Strategy(
            id = "fake_disorder",
            titleRes = R.string.strategy_fake_disorder,
            descriptionRes = R.string.strategy_fake_disorder_desc,
            args = "--disorder 1 --fake -1",
        ),
        Strategy(
            id = "md5sig",
            titleRes = R.string.strategy_md5sig,
            descriptionRes = R.string.strategy_md5sig_desc,
            args = "--fake -1 --md5sig",
        ),
        Strategy(
            id = "oob",
            titleRes = R.string.strategy_oob,
            descriptionRes = R.string.strategy_oob_desc,
            args = "--oob 3+s",
        ),
        Strategy(
            id = "tlsrec_auto",
            titleRes = R.string.strategy_tlsrec_auto,
            descriptionRes = R.string.strategy_tlsrec_auto_desc,
            args = "--auto=torst --timeout 3 --tlsrec 3+s",
        ),
        Strategy(
            id = ID_NONE,
            titleRes = R.string.strategy_none,
            descriptionRes = R.string.strategy_none_desc,
            args = "",
        ),
    )

    fun byId(id: String): Strategy = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
