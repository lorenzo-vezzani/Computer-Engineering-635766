import sys
import os
import yaml
from pathlib import Path
from jinja2 import Environment, FileSystemLoader

DBG_PRINT_TEMPLATE = False
DBG_PRINT_DATA = False
DBG_PRINT_CONTENT = False

TEMPLATE_FRR = 'template_frr.j2'
TEMPLATE_ALP = 'template_alp.j2'

BASE_DIR = Path(__file__).resolve().parent

# python.exe .\renderer.py .\info_ceos2.yaml
# python.exe .\renderer.py .\info_ceos1.yaml

# ========= Get arguments
if len(sys.argv) != 2:
    print("Error: must specify <data> as arguments")
    print("Syntax:")
    print("[python.exe | python3] [renderer.py] [data.yaml]")
    print("WIN: \tpython.exe .\\renderer.py [data.yaml]")
    sys.exit(1)


# Data should be second argument
data_filename = sys.argv[1]
if not os.path.isfile(data_filename):
    print(f"Error: '{data_filename}' is not a valid file")
    print("Syntax:")
    print("[python.exe | python3] [renderer.py] [data.yaml]")
    print("WIN: \tpython.exe .\\renderer.py [data.yaml]")
    sys.exit(1)


# ========= Load data
with open(data_filename) as file:
    data = yaml.safe_load(file)

# Default type: FRR
cont_type = 'FRR' 
if 'type' in data:
    if data['type'].lower() == 'alpine':
        cont_type = 'alpine'

if DBG_PRINT_DATA:
    print(data)

# ========= Load template
# Load template
environment = Environment(
    loader=FileSystemLoader(str(BASE_DIR)),
    trim_blocks=True,
    lstrip_blocks=True
)
template = None
if cont_type == 'FRR':
    template = environment.get_template(TEMPLATE_FRR)
elif cont_type == 'alpine':
    template = environment.get_template(TEMPLATE_ALP)

if template is None:
    print("Template / container type not recognized")
    print("Template / container must be FRR or Alpine")
    sys.exit(1)

# Print for debug
if DBG_PRINT_TEMPLATE:
    print(template)


# ========= Generate content
content = template.render(data)


# ========= Print and save to file

if DBG_PRINT_CONTENT:
    print(content)


if 'config_filename' not in data:
    print("Error: config_filename missing in data file")
    sys.exit(1)

output_dir = BASE_DIR / ".." / "config" / "startup"
output_dir = output_dir.resolve()

config_filename = data['config_filename']
output_path = output_dir / config_filename

output_dir.mkdir(parents=True, exist_ok=True)

with open(output_path, "w") as f:
    f.write(content)

