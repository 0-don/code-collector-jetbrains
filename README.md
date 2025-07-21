# Code Collector

IntelliJ plugin that collects Java/Kotlin code context for AI assistance by analyzing imports and
gathering related files.

## Features

- **Smart Import Analysis** - Follows import chains to collect related local files
- **Java & Kotlin Support** - Full PSI integration with K2 compatibility
- **AI-Optimized Output** - Formatted with line numbers and file headers
- **Configurable Filtering** - Exclude files with glob patterns
- **Clipboard Integration** - Auto-copies formatted output

## Installation

### Plugin Repository

`File → Settings → Plugins` → Search "Code Collector"

### Manual

1. Download JAR from releases
2. `File → Settings → Plugins → Install Plugin from Disk`
3. Restart IntelliJ

## Usage

### Code Collect (Import-based)

- Right-click files → `Code Collect`
- Shortcut: `Ctrl+Shift+G`
- Follows imports to collect related files

### Code Collect All

- Right-click in Project Explorer → `Code Collect All`
- Collects entire codebase (respects ignore patterns)

## Configuration

`File → Settings → Tools → Code Collector`

Configure ignore patterns (glob format):

- `target/**`, `build/**` - Build outputs
- `.gradle/**`, `.idea/**` - Tool directories
- `*.jar`, `*.log` - Archives and logs