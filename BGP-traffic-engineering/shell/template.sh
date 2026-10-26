#!/bin/bash

# Generate files
for file in template/data/*.yaml; do
    python3 template/generator.py "$file"
    echo "$file" processed
done

# Make sh executable
for shfile in config/startup/*.sh; do
    chmod +x "$shfile"
done
