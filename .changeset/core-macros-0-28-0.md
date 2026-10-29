---
'expo-modules-core': patch
---

[iOS] Update `expo-modules-macros` to 0.28.0. The code that `@Record` and `@Union` generate is `nonisolated`, so it also compiles with `-default-isolation MainActor`, and a `@Union` can be a `@Record` property. A `@Record` type can no longer be created from a native dictionary: its `from(dictionary:appContext:)` throws `RecordDictionaryConversionException`.
