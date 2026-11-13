package qtags

// `import qtags.*` brings in every tag and attribute. Where a name is both a
// tag and an attribute, the more commonly used one is exported here; the other
// is still available through its object, e.g. `tags.title`, `attrs.form`.
//
// Tag wins:       abbr cite form label slot span
// Attribute wins: data style title
export tags.{data as _, style as _, title as _, *}
export attrs.{abbr as _, cite as _, form as _, label as _, slot as _, span as _, *}
