package com.wifizone.mikhmon.tickets

import com.wifizone.mikhmon.tickets.gfx.Colors
import com.wifizone.mikhmon.tickets.templates.AfricaTemplate
import com.wifizone.mikhmon.tickets.templates.AfricaTemplate.Motif
import com.wifizone.mikhmon.tickets.templates.AuroraTemplate
import com.wifizone.mikhmon.tickets.templates.CouponTemplate
import com.wifizone.mikhmon.tickets.templates.MiniTemplate
import com.wifizone.mikhmon.tickets.templates.PolaroidTemplate
import com.wifizone.mikhmon.tickets.templates.RibbonTemplate
import com.wifizone.mikhmon.tickets.templates.SportTemplate
import com.wifizone.mikhmon.tickets.templates.StandardTemplate
import com.wifizone.mikhmon.tickets.templates.TechTemplate
import com.wifizone.mikhmon.tickets.templates.ThermalTemplate
import com.wifizone.mikhmon.tickets.templates.WaveTemplate
import com.wifizone.mikhmon.tickets.templates.BoardingTemplate
import com.wifizone.mikhmon.tickets.templates.CardTemplate
import com.wifizone.mikhmon.tickets.templates.LuxeTemplate
import com.wifizone.mikhmon.tickets.templates.MinimalTemplate
import com.wifizone.mikhmon.tickets.templates.EventTemplate
import com.wifizone.mikhmon.tickets.templates.NeonTemplate

/** Tous les modèles de tickets. Les identifiants sont définitifs (ils servent aux achats). */
object Catalog {
    val all: List<TicketTemplate> by lazy {
        fun c(vararg h: String) = h.map { Colors.hex(it) }.toIntArray()
        listOf(
            StandardTemplate(),
            MiniTemplate(),
            CardTemplate("tpl-carte-or", "Carte Or", Palette.of("#FFF", "#FFF", "#9A6B12", "#FFD873", "#FFF", "#E9DDC2")),
            CardTemplate("tpl-carte-platine", "Carte Platine", Palette.of("#FFF", "#FFF", "#2B2F36", "#C9D1D9", "#FFF", "#AAB2BD"), chipGold = false),
            CardTemplate("tpl-carte-saphir", "Carte Saphir", Palette.of("#FFF", "#FFF", "#1E3A8A", "#60A5FA", "#FFF", "#BFD7FF")),
            EventTemplate("tpl-billet-rouge", "Billet Rouge", Palette.of("#FFF", "#FFFBF5", "#E11D48", "#F59E0B", "#1F1720", "#8C7F86")),
            EventTemplate("tpl-billet-violet", "Billet Violet", Palette.of("#FFF", "#FBF9FF", "#7C3AED", "#EC4899", "#1C1630", "#857E99")),
            EventTemplate("tpl-billet-turquoise", "Billet Turquoise", Palette.of("#FFF", "#F6FFFD", "#0D9488", "#F59E0B", "#0F2524", "#6E8785")),
            NeonTemplate("tpl-neon-cyan", "Néon Cyan", Palette.of("#0B0B14", "#14141F", "#22D3EE", "#A78BFA", "#FFF", "#9AA3B2")),
            NeonTemplate("tpl-neon-rose", "Néon Rose", Palette.of("#0E0912", "#1A1020", "#F472B6", "#FDE047", "#FFF", "#B3A3B7")),
            NeonTemplate("tpl-neon-vert", "Néon Vert", Palette.of("#07110B", "#0E1C14", "#4ADE80", "#22D3EE", "#FFF", "#9DB3A5")),
            BoardingTemplate("tpl-embarquement-bleu", "Embarquement Bleu", Palette.of("#FFF", "#FFFFFF", "#2563EB", "#06B6D4", "#0F172A", "#64748B")),
            BoardingTemplate("tpl-embarquement-nuit", "Embarquement Nuit", Palette.of("#FFF", "#FFFFFF", "#111827", "#6366F1", "#111827", "#6B7280")),
            BoardingTemplate("tpl-embarquement-corail", "Embarquement Corail", Palette.of("#FFF", "#FFFDFB", "#F43F5E", "#F97316", "#2A1215", "#8A6F72")),
            AuroraTemplate("tpl-aurore", "Aurore", Palette.of("#FFF", "#FFF", "#4F46E5", "#EC4899", "#FFF", "#EEE"), Colors.hex("#22D3EE")),
            AuroraTemplate("tpl-aurore-couchant", "Coucher de soleil", Palette.of("#FFF", "#FFF", "#F97316", "#DB2777", "#FFF", "#EEE"), Colors.hex("#FACC15")),
            AuroraTemplate("tpl-aurore-lagon", "Lagon", Palette.of("#FFF", "#FFF", "#0891B2", "#10B981", "#FFF", "#EEE"), Colors.hex("#A3E635")),
            MinimalTemplate("tpl-minimal-encre", "Minimal Encre", Palette.of("#FFF", "#FFFFFF", "#111827", "#111827", "#111827", "#6B7280")),
            MinimalTemplate("tpl-minimal-corail", "Minimal Corail", Palette.of("#FFF", "#FFFFFF", "#F43F5E", "#F43F5E", "#1F2937", "#6B7280")),
            MinimalTemplate("tpl-minimal-emeraude", "Minimal Émeraude", Palette.of("#FFF", "#FFFFFF", "#059669", "#059669", "#1F2937", "#6B7280")),
            LuxeTemplate("tpl-luxe-noir", "Luxe Noir & Or", Palette.of("#14110D", "#14110D", "#14110D", "#D4AF37", "#F5EEDC", "#B9AE96")),
            LuxeTemplate("tpl-luxe-emeraude", "Luxe Émeraude", Palette.of("#0B3B2E", "#0B3B2E", "#0B3B2E", "#E2C275", "#F3EFE2", "#B9C4B5")),
            LuxeTemplate("tpl-luxe-bordeaux", "Luxe Bordeaux", Palette.of("#4A0E1C", "#4A0E1C", "#4A0E1C", "#E8C27A", "#FBEFE6", "#D2B7AE")),
            AfricaTemplate("tpl-kente", "Kente", Palette.of("#FFF", "#FFFDF6", "#0E7C3A", "#F2B705", "#1E1A12", "#7A7262"), Motif.KENTE, c("#F2B705", "#0E7C3A", "#C1121F", "#111111")),
            AfricaTemplate("tpl-bogolan", "Bogolan", Palette.of("#FFF", "#FBF6EE", "#7A4A24", "#C08A4B", "#2B1D12", "#857463"), Motif.BOGOLAN, c("#6B4226", "#F3E6CF")),
            AfricaTemplate("tpl-wax", "Wax", Palette.of("#FFF", "#FFFBF6", "#1D4ED8", "#F97316", "#151A2C", "#6B7280"), Motif.WAX, c("#1D4ED8", "#F97316", "#FDE68A", "#0F172A")),
            WaveTemplate("tpl-vague-ocean", "Vague Océan", Palette.of("#FFF", "#FFFFFF", "#0284C7", "#22D3EE", "#0F172A", "#64748B")),
            WaveTemplate("tpl-vague-lavande", "Vague Lavande", Palette.of("#FFF", "#FFFFFF", "#7C3AED", "#F0ABFC", "#1E1B2E", "#6B6880")),
            WaveTemplate("tpl-vague-mangue", "Vague Mangue", Palette.of("#FFF", "#FFFFFF", "#EA580C", "#FACC15", "#2A160A", "#7C6A5E")),
            CouponTemplate("tpl-coupon-kraft", "Coupon Kraft", Palette.of("#E8D3B0", "#F7EBD6", "#9A3412", "#9A3412", "#3B2A1A", "#7A6650")),
            CouponTemplate("tpl-coupon-menthe", "Coupon Menthe", Palette.of("#DDF5EA", "#F5FFFA", "#047857", "#047857", "#12352A", "#5E7F72")),
            CouponTemplate("tpl-coupon-citron", "Coupon Citron", Palette.of("#FFF4BF", "#FFFDF0", "#B45309", "#B45309", "#3A2C08", "#7C6B3A")),
            TechTemplate("tpl-tech-matrix", "Tech Matrix", Palette.of("#050A06", "#050A06", "#22C55E", "#22C55E", "#D7F8E0", "#7FA58A")),
            TechTemplate("tpl-tech-circuit", "Tech Circuit", Palette.of("#06122B", "#06122B", "#38BDF8", "#38BDF8", "#E0F2FE", "#7EA2C3")),
            TechTemplate("tpl-tech-titane", "Tech Titane", Palette.of("#1B1E23", "#1B1E23", "#F59E0B", "#F59E0B", "#F3F4F6", "#9CA3AF")),
            RibbonTemplate("tpl-fete", "Fête", Palette.of("#FFF", "#FFFFFF", "#DB2777", "#FACC15", "#1F1630", "#7A6E88"), c("#DB2777", "#FACC15", "#22D3EE", "#8B5CF6", "#22C55E")),
            RibbonTemplate("tpl-royal", "Royal", Palette.of("#FFF", "#FFFDF8", "#4338CA", "#D4AF37", "#1B1840", "#706C93"), c("#4338CA", "#D4AF37", "#A5B4FC")),
            RibbonTemplate("tpl-sahel", "Sahel", Palette.of("#FFF", "#FFFAF3", "#C2410C", "#0F766E", "#2B1B10", "#80705F"), c("#C2410C", "#0F766E", "#F59E0B")),
            PolaroidTemplate("tpl-polaroid-soleil", "Polaroid Soleil", Palette.of("#FFF", "#FFFFFF", "#EA580C", "#FACC15", "#1F1A17", "#7C7470"), c("#FDE68A", "#FB923C", "#E11D48")),
            PolaroidTemplate("tpl-polaroid-nuit", "Polaroid Nuit", Palette.of("#FFF", "#FFFFFF", "#4F46E5", "#22D3EE", "#15142B", "#6E6C88"), c("#312E81", "#6D28D9", "#DB2777")),
            PolaroidTemplate("tpl-polaroid-sable", "Polaroid Sable", Palette.of("#FFF", "#FFFDF8", "#0E7490", "#F59E0B", "#22201C", "#7C766D"), c("#BAE6FD", "#FDE68A", "#D97706")),
            SportTemplate("tpl-sport-turbo", "Sport Turbo", Palette.of("#111111", "#111111", "#DC2626", "#FACC15", "#FFF", "#AAA")),
            SportTemplate("tpl-sport-electrique", "Sport Électrique", Palette.of("#0B0B0F", "#0B0B0F", "#FACC15", "#38BDF8", "#FFF", "#AAA")),
            SportTemplate("tpl-sport-jungle", "Sport Jungle", Palette.of("#0E1A12", "#0E1A12", "#16A34A", "#A3E635", "#FFF", "#AAA")),
            ThermalTemplate("tpl-thermique-pro", "Reçu thermique Pro", withQr = true),
            ThermalTemplate("tpl-thermique-compact", "Reçu thermique compact", withQr = false),
        )
    }

    fun byId(id: String): TicketTemplate? = all.firstOrNull { it.id == id }
    val free get() = all.filter { !it.premium }
    val premium get() = all.filter { it.premium }
}
