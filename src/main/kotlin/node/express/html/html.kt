package node.express.html

@DslMarker
annotation class HTMLMaker

@HTMLMaker
abstract class Element {
    abstract fun render(builder: StringBuilder, indent: String)

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

@HTMLMaker
class TextElement(val text: String) : Element() {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append(text)
    }
}

@HTMLMaker
abstract class Tag(val name: String) : Element() {
    val children = arrayListOf<Element>()
    val attributes = hashMapOf<String, String>()

    fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("<$name${renderAttributes()}>")
        for (c in children)
            c.render(builder, indent + "  ")
        builder.append("</$name>")
    }

    fun renderAttributes(): String? {
        val builder = StringBuilder()
        for (a in attributes.keys)
            builder.append(" $a=\"${attributes[a]}\"")
        return builder.toString()
    }

    fun Element.plus() {
        this@Tag.children.add(this)
    }

    fun attribute(key: String, value: String?) {
        if (value != null)
            attributes[key] = value
    }
}

@HTMLMaker
abstract class UnclosedTag(name: String): Tag(name) {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("<$name${renderAttributes()}>")
    }
}

@HTMLMaker
abstract class TagWithText(name: String) : Tag(name) {
    fun String.plus() {
        children.add(TextElement(this))
    }
}

@HTMLMaker
class HTML(clas: String? = null, init: HTML.() -> Unit) : TagWithText("html") {
    init {
        init()
    }

    fun head(init: Head.() -> Unit) = initTag(Head(), init)

    fun body(init: Body.() -> Unit) = initTag(Body(), init)

    override fun render(builder: StringBuilder, indent: String) {
        builder.appendln("<!DOCTYPE html>")
        super.render(builder, indent)
    }
}

@HTMLMaker
class Head : TagWithText("head") {
    fun base(href: String) = children.add(Base(href))

    fun title(init: Title.() -> Unit) = initTag(Title(), init)

    fun meta(vararg values: Pair<String, String>) = children.add(Meta(*values))

    fun link(rel: String? = null,
             href: String? = null,
             type: String? = null) = children.add(Link(rel, href, type))

    fun script(src: String) = children.add(Script(src))
    fun css(src: String) = link(rel = "stylesheet", href=src, type="text/css")
}

@HTMLMaker
class Meta(vararg values: Pair<String, String>): UnclosedTag("meta") {
    init {
        values.forEach { attributes[it.first] = it.second }
    }
}

@HTMLMaker
class Link(rel: String?,
           href: String?,
           type: String?): UnclosedTag("link") {
    init {
        attribute("rel", rel)
        attribute("href", href)
        attribute("type", type)
    }
}

@HTMLMaker
class Script(src: String): Tag("script") {
    init {
        attribute("src", src)
    }
}

@HTMLMaker
class Base(href: String): UnclosedTag("base") {
    init {
        attribute("href", href)
    }
}

@HTMLMaker
class Title : TagWithText("title")

@HTMLMaker
abstract class BodyTag(name: String, clas: String? = null) : TagWithText(name) {
    var clas: String?
        get() = attributes["class"]
        set(value) {
            attribute("class", value)
        }
}

@HTMLMaker
abstract class GeneralBodyTag(name: String, clas: String? = null) : BodyTag(name, clas) {
    fun b(clas: String? = null, init: B.() -> Unit) = initTag(B(clas), init)
    fun p(clas: String? = null, init: P.() -> Unit) = initTag(P(clas), init)

    fun header(clas: String? = null, init: HEADER.() -> Unit) = initTag(HEADER(clas), init)

    fun h1(clas: String? = null, init: H1.() -> Unit) = initTag(H1(clas), init)
    fun h2(clas: String? = null, init: H2.() -> Unit) = initTag(H2(clas), init)
    fun h3(clas: String? = null, init: H3.() -> Unit) = initTag(H3(clas), init)
    fun h4(clas: String? = null, init: H4.() -> Unit) = initTag(H4(clas), init)
    fun h5(clas: String? = null, init: H5.() -> Unit) = initTag(H5(clas), init)
    fun h6(clas: String? = null, init: H6.() -> Unit) = initTag(H6(clas), init)

    fun ul(clas: String? = null, init: UL.() -> Unit) = initTag(UL(clas), init)

    fun div(clas: String? = null, init: DIV.() -> Unit) {
        val div = initTag(DIV(), init)
        if (clas != null) div.clas = clas
    }
    fun a(href: String, clas: String? = null, init: A.() -> Unit) = initTag(A(href, clas), init)
    operator fun String.unaryPlus() = children.add(TextElement(this))
}

@HTMLMaker
class Body : GeneralBodyTag("body")

@HTMLMaker
class UL(clas: String? = null) : BodyTag("ul") {
    fun li(clas: String? = null, init: LI.() -> Unit) = initTag(LI(clas), init)
}

@HTMLMaker
class B(clas: String? = null) : GeneralBodyTag("b", clas)

@HTMLMaker
class LI(clas: String? = null) : GeneralBodyTag("li", clas)

@HTMLMaker
class P(clas: String? = null) : GeneralBodyTag("p", clas)

@HTMLMaker
class H1(clas: String? = null) : GeneralBodyTag("h1", clas)

@HTMLMaker
class H2(clas: String? = null) : GeneralBodyTag("h2", clas)

@HTMLMaker
class H3(clas: String? = null) : GeneralBodyTag("h3", clas)

@HTMLMaker
class H4(clas: String? = null) : GeneralBodyTag("h4", clas)

@HTMLMaker
class H5(clas: String? = null) : GeneralBodyTag("h5", clas)

@HTMLMaker
class H6(clas: String? = null) : GeneralBodyTag("h6", clas)

@HTMLMaker
class HEADER(clas: String? = null) : GeneralBodyTag("header", clas)

@HTMLMaker
class DIV(clas: String? = null): GeneralBodyTag("div", clas)

@HTMLMaker
class A(inHref: String, clas: String? = null) : GeneralBodyTag("a") {
    var href: String
        get() = attributes["href"]!!
        set(value) {
            attributes["href"] = value
        }

    init {
        this.href = inHref
        this.clas = clas
    }
}

fun html(init: HTML.() -> Unit) = HTML(init = init)
