#!/bin/bash

set -e

echo "Configuring git to use .githooks/..."
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
echo "Done! Git hooks are active."
