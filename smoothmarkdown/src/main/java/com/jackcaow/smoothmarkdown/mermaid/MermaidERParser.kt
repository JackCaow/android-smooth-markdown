package com.jackcaow.smoothmarkdown.mermaid

/** Flutter structured ER subset: entities, aliases, attribute blocks and typed relations. */
class MermaidERParser {
    fun parse(lines: List<String>): MermaidDiagram? {
        if (!lines.firstOrNull().equals("erDiagram", true)) return null
        val entities = linkedMapOf<String, MermaidEREntity>()
        val relationships = mutableListOf<MermaidERRelationship>()
        var direction = MermaidDirection.TB
        var openBlock: String? = null
        for (original in lines.drop(1)) {
            val line = original.trim().removeSuffix(";").trim()
            if (line.isEmpty()) continue
            if (openBlock != null) {
                if (line == "}") { openBlock = null; continue }
                if ('{' in line || '}' in line) return null
                val entity = entities.getValue(openBlock)
                entities[openBlock] = entity.copy(attributes = entity.attributes + line)
                continue
            }
            if (line.startsWith("direction ", true)) {
                direction = when (line.substring(10).trim().uppercase()) {
                    "TB", "TD" -> MermaidDirection.TB
                    "BT" -> MermaidDirection.BT
                    "LR" -> MermaidDirection.LR
                    "RL" -> MermaidDirection.RL
                    else -> return null
                }
                continue
            }
            val relation = parseRelationship(line)
            if (relation != null) {
                entities.putIfAbsent(relation.from, MermaidEREntity(relation.from, relation.from, emptyList()))
                entities.putIfAbsent(relation.to, MermaidEREntity(relation.to, relation.to, emptyList()))
                relationships += relation
                continue
            }
            val block = line.endsWith('{')
            val declaration = if (block) line.dropLast(1).trim() else line
            val parsed = parseDeclaration(declaration) ?: return null
            val previous = entities[parsed.first]
            entities[parsed.first] = MermaidEREntity(parsed.first, parsed.second ?: previous?.label ?: parsed.first,
                previous?.attributes.orEmpty())
            if (block) openBlock = parsed.first
        }
        if (openBlock != null || entities.isEmpty()) return null
        val data = MermaidERData(entities.values.toList(), relationships, direction)
        return MermaidDiagram(MermaidKind.ERDiagram, direction, emptyList(), emptyList(), er = data)
    }

    private fun parseRelationship(line: String): MermaidERRelationship? {
        val operator = operatorRegex.find(line) ?: return null
        val from = parseIdentity(line.substring(0, operator.range.first).trim()) ?: return null
        val tail = line.substring(operator.range.last + 1).trim()
        val colon = tail.indexOf(':')
        if (colon < 1) return null
        val to = parseIdentity(tail.substring(0, colon).trim()) ?: return null
        val label = tail.substring(colon + 1).trim().removeSurrounding("\"").takeIf(String::isNotEmpty) ?: return null
        val source = cardinality(operator.groupValues[1]) ?: return null
        val target = cardinality(operator.groupValues[3]) ?: return null
        return MermaidERRelationship(from, to, label, source, target, operator.groupValues[2] == "..")
    }

    private fun cardinality(marker: String): MermaidERCardinality? = when (marker) {
        "||" -> MermaidERCardinality.ExactlyOne
        "|o", "o|" -> MermaidERCardinality.ZeroOrOne
        "}|", "|{" -> MermaidERCardinality.OneOrMore
        "}o", "o{" -> MermaidERCardinality.ZeroOrMore
        else -> null
    }

    private fun parseDeclaration(text: String): Pair<String, String?>? {
        val bracket = text.indexOf('[')
        if (bracket < 0) return parseIdentity(text)?.let { it to null }
        if (!text.endsWith(']')) return null
        val id = parseIdentity(text.substring(0, bracket).trim()) ?: return null
        val rawLabel = text.substring(bracket + 1, text.length - 1).trim()
        val label = rawLabel.removeSurrounding("\"").takeIf(String::isNotEmpty) ?: return null
        return id to label
    }

    private fun parseIdentity(text: String): String? {
        if (text.startsWith('"') && text.endsWith('"') && text.length > 2) {
            return text.substring(1, text.length - 1).takeIf { it.isNotBlank() && '"' !in it }
        }
        return text.takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } }
    }

    private companion object {
        // Explicit closing-brace escapes keep this valid with Android ICU as well as desktop JVM regex.
        val operatorRegex = Regex("(\\|\\||\\|o|o\\||\\}\\||\\}o)(--|\\.\\.)(\\|\\||o\\||\\|o|\\|\\{|o\\{)")
    }
}
