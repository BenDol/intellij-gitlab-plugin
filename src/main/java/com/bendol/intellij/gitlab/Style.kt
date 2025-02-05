package com.bendol.intellij.gitlab

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.border.Border

object Style {

    object Images {
        val ProjectIcon: Icon = IconLoader.getIcon("/images/logo.png", Images::class.java)
        val SuccessIcon: Icon = IconLoader.getIcon("/images/success.png", Images::class.java)
        val FailedIcon:  Icon = IconLoader.getIcon("/images/failed.png", Images::class.java)
        val RunningIcon: Icon = IconLoader.getIcon("/images/running.png", Images::class.java)
        val PendingIcon: Icon = IconLoader.getIcon("/images/running.png", Images::class.java)
        val SkippedIcon: Icon = IconLoader.getIcon("/images/running.png", Images::class.java)
        val UnknownIcon: Icon = IconLoader.getIcon("/images/running.png", Images::class.java)
    }

    @Suppress("UseJBColor")
    object Colors {
        val TEXT_SUCCESS    = ColorStyle("#9cff9c")
        val TEXT_FAILED     = ColorStyle("#ff8080")
        val TEXT_RUNNING    = ColorStyle(JBColor.foreground())
        val TEXT_PENDING    = ColorStyle(JBColor.foreground())
        val TEXT_CANCELED   = ColorStyle(JBColor.foreground())
        val TEXT_SKIPPED    = ColorStyle(JBColor.foreground())
        val TEXT_MANUAL     = ColorStyle(JBColor.foreground())
        val TEXT_SCHEDULED  = ColorStyle(JBColor.foreground())
        val TEXT_UNKNOWN    = ColorStyle(JBColor.foreground())

        val TREE_NO_SELECT_BACKGROUND = ColorStyle(Color(0, 0, 0, 0))
        val TREE_SELECTED_BACKGROUND  = ColorStyle(Color(0, 0, 0, 0))

        val BACKGROUND_PANEL = ColorStyle(JBColor.background())
    }

    object Fonts {
        val TREE_NODE = FontStyle(size = 14)
    }

    object Borders {
        val PANEL_DEFAULT = EmptyBorderStyle(5, 5, 5, 5)
    }
}

class ColorStyle {
    var value: String = ""
        set(value) {
            field = value
                .replace("#", "")
                .replace("0x", "")
            if (color == null) {
                color = asColor()
            }
        }
    private var color: Color? = null
        set(color) {
            field = color
            if (color != null && this.value.isEmpty()) {
                this.value = convertColorToHex(color)
            }
        }

    constructor(value: String) {
        this.value = value
        this.color = asColor()
    }
    constructor(color: Color) {
        this.color = color
        this.value = convertColorToHex(color)
    }

    fun asHash(): String {
        return "#$value"
    }

    fun asRgb(): String {
        return "rgb($value)"
    }

    fun asHex(): String {
        return "0x$value"
    }

    fun asInt(): Int {
        return value.toInt(16)
    }

    fun asColor(): Color {
        return color ?: Color.decode(asHex())
    }

    companion object {
        fun convertColorToRGB(color: Color): String {
            return String.format("rgb(%d, %d, %d)", color.red, color.green, color.blue)
        }
        fun convertColorToHex(color: Color): String {
            return String.format("0x%02x%02x%02x", color.red, color.green, color.blue)
        }
    }
}

class FontStyle(
    val size: Int? = null,
    val style: Int? = null,
    val family: String? = null
) {

    fun toFont(): Font {
        return Font(family, style ?: Font.PLAIN, size ?: 12)
    }

    fun derive(font: Font): Font {
        return font.deriveFont(style ?: font.style, size?.toFloat() ?: font.size.toFloat())
    }

    fun shouldDerive(): Boolean {
        return size == null || style == null || family == null
    }
}

open class BorderStyle(private val border: Border) {
    open fun asBorder(): Border {
        return border
    }
}

class LineBorderStyle(
    color: ColorStyle,
    width: Int,
    rounded: Boolean = false
): BorderStyle(BorderFactory.createLineBorder(color.asColor(), width, rounded))

class EmptyBorderStyle(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int
): BorderStyle(BorderFactory.createEmptyBorder(top, left, bottom, right))