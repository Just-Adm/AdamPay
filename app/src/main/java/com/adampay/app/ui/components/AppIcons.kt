package com.adampay.app.ui.components

import androidx.annotation.DrawableRes
import com.adampay.app.R

/**
 * Icons a category or pocket can use, stored in the database by key (never by resource id, which
 * changes between builds). Unknown keys fall back to a neutral icon.
 */
object AppIcons {
    val categoryIcons: List<Pair<String, Int>> = listOf(
        "lunch_dining" to R.drawable.ic_lunch_dining,
        "restaurant" to R.drawable.ic_restaurant,
        "fastfood" to R.drawable.ic_fastfood,
        "local_pizza" to R.drawable.ic_local_pizza,
        "kebab_dining" to R.drawable.ic_kebab_dining,
        "ramen_dining" to R.drawable.ic_ramen_dining,
        "bakery_dining" to R.drawable.ic_bakery_dining,
        "local_cafe" to R.drawable.ic_local_cafe,
        "coffee" to R.drawable.ic_coffee,
        "icecream" to R.drawable.ic_icecream,
        "cookie" to R.drawable.ic_cookie,
        "local_taxi" to R.drawable.ic_local_taxi,
        "directions_bus" to R.drawable.ic_directions_bus,
        "train" to R.drawable.ic_train,
        "two_wheeler" to R.drawable.ic_two_wheeler,
        "local_gas_station" to R.drawable.ic_local_gas_station,
        "mobile" to R.drawable.ic_mobile,
        "headphones" to R.drawable.ic_headphones,
        "celebration" to R.drawable.ic_celebration,
        "movie" to R.drawable.ic_movie,
        "music_note" to R.drawable.ic_music_note,
        "sports_soccer" to R.drawable.ic_sports_soccer,
        "fitness_center" to R.drawable.ic_fitness_center,
        "checkroom" to R.drawable.ic_checkroom,
        "shopping_bag" to R.drawable.ic_shopping_bag,
        "content_cut" to R.drawable.ic_content_cut,
        "sports_esports" to R.drawable.ic_sports_esports,
        "toys" to R.drawable.ic_toys,
        "redeem" to R.drawable.ic_redeem,
        "volunteer_activism" to R.drawable.ic_volunteer_activism,
        "school" to R.drawable.ic_school,
        "menu_book" to R.drawable.ic_menu_book,
        "medical_services" to R.drawable.ic_medical_services,
        "pets" to R.drawable.ic_pets,
        "storefront" to R.drawable.ic_storefront,
        "family_restroom" to R.drawable.ic_family_restroom,
        "toll" to R.drawable.ic_toll,
        "work" to R.drawable.ic_work,
        "sell" to R.drawable.ic_sell,
        "handshake" to R.drawable.ic_handshake,
        "paid" to R.drawable.ic_paid,
        "star" to R.drawable.ic_star,
        "diamond" to R.drawable.ic_diamond,
        "mosque" to R.drawable.ic_mosque,
        "more_horiz" to R.drawable.ic_more_horiz,
    )

    val pocketIcons: List<Pair<String, Int>> = listOf(
        "wallet" to R.drawable.ic_wallet,
        "account_balance_wallet" to R.drawable.ic_account_balance_wallet,
        "cottage" to R.drawable.ic_cottage,
        "savings" to R.drawable.ic_savings,
        "backpack" to R.drawable.ic_backpack,
        "inventory_2" to R.drawable.ic_inventory_2,
        "key" to R.drawable.ic_key,
        "lock" to R.drawable.ic_lock,
        "payments" to R.drawable.ic_payments,
        "diamond" to R.drawable.ic_diamond,
        "beach_access" to R.drawable.ic_beach_access,
        "star" to R.drawable.ic_star,
    )

    private val all: Map<String, Int> = (categoryIcons + pocketIcons).toMap()

    @DrawableRes
    fun of(key: String?): Int = all[key] ?: R.drawable.ic_category
}
