fun findSubtrees(s: String): List<String> = buildList {
    var substring = ""
    var depth = 0
    for (i in s.indices) {
        val c = s[i]
        if (c == '(') {
            depth += 1
        } else if (c == ')') {
            depth -= 1
        }
        if (depth >= 1){
            substring += c
        }
        if (depth == 0) {
            add(substring)
            substring = ""
        }
    }
}

fun parseTree(s: String, parent: MergeTree? = null): MergeTree {
    //assert(s.first() == '(' && s.last() == ')')
    val i = s.withIndex().firstOrNull() { it.index > 0 && it.value == '(' }?.index ?: s.length
    val h = s.substring(1, i)

    val tree = MergeTree(h.toDouble() *100, parent=parent)
    val subtrees = if (i == s.length) emptyList() else findSubtrees(s.substring(i, s.length)).map { parseTree(it, tree) }
    tree.children.addAll(subtrees)

    return tree
}