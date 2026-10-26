import os
import sys
import socket
import argparse
import paramiko

NODE_IP_MAP = {
    "n1":   {"ip": "10.255.255.201", "image": "linux"},
    "n2":   {"ip": "10.255.255.202", "image": "linux"},
    "n3":   {"ip": "10.255.255.203", "image": "linux"},
    "n4":   {"ip": "10.255.255.204", "image": "linux"},

    "mngr": {"ip": "10.255.255.205", "image": "linux"},

    "CE1":  {"ip": "10.255.255.101", "image": "frr"},
    "CE2":  {"ip": "10.255.255.102", "image": "frr"},

    "PE1":  {"ip": "10.255.255.111", "image": "frr"},
    "PE2":  {"ip": "10.255.255.112", "image": "frr"},
    "GW1":  {"ip": "10.255.255.121", "image": "frr"},
    "GW2":  {"ip": "10.255.255.122", "image": "frr"},

    "UP1":  {"ip": "10.255.255.131", "image": "frr"},
    "UP2":  {"ip": "10.255.255.132", "image": "frr"},
    
    "INT":  {"ip": "10.255.255.141", "image": "frr"},
}


USERNAME = "root"
PASSWORD = "admin"

CMD_MAP = {
    "bgp sum": {
        "frr": "vtysh -c 'show bgp summary'",
        "linux": None,
    },
    "bgp det": {
        "frr": "vtysh -c 'show bgp ipv4 detail'",
        "linux": None,
    },
    "int bri": {
        "frr": "vtysh -c 'show int brief'",
        "linux": "ip -br addr",
    },
    "int desc": {
        "frr": "vtysh -c 'show int description'",
        "linux": "ip link show",
    },
    "ip route": {
        "frr": "vtysh -c 'show ip route'",
        "linux": "ip route show",
    },
}


def run_ssh_cmd(host, cmd, timeout=10):
    """
    Execute a command on a remote host via SSH and return its output.

    :param host: Target hostname or IP address.
    :param cmd: Shell command to execute on the remote host.
    :param timeout: Timeout in seconds for SSH connection and operations.
    """

    # Initialize SSH client
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    # Ensure the SSH client is always closed
    try:

        # Establish SSH connection using predefined credentials
        client.connect(
            hostname=host,
            username=USERNAME,
            password=PASSWORD,
            timeout=timeout,
            banner_timeout=timeout,
            auth_timeout=timeout,
        )

        # Execute the remote command and collect stdout and stderr
        stdin, stdout, stderr = client.exec_command(cmd)
        out = stdout.read().decode(errors="ignore")
        err = stderr.read().decode(errors="ignore")

        # Return cleaned command output
        return out.strip(), err.strip()

    # Close the SSH connection
    finally:
        client.close()


def execute_cmd(cmd_key, targets, custom_cmd=None):
    """
    Execute a command via SSH on multiple target nodes, selecting
    the command based on the node's image type.

    :param cmd_key: Key of the command in CMD_MAP.
    :param targets: Dictionary mapping node names to {'ip', 'image'}.
    """

    # Iterate over all target nodes
    for name, data in targets.items():
        ip = data["ip"]
        image = data["image"]

        # Use custom command if provided, otherwise select from CMD_MAP
        cmd = custom_cmd or CMD_MAP.get(cmd_key, {}).get(image)
        if not cmd:
            print(f"{name} ({ip}) skipped: no command for image '{image}'")
            continue

        try:
            # Run command on the current target
            out, err = run_ssh_cmd(ip, cmd)

            # Print standard output if present
            if out:
                print(f"\n=== {name} ({ip}) ===")
                print(out)

            # Print standard error if present
            if err:
                print(f"\n=== {name} ({ip}) === [stderr]")

        # Handle SSH and network-related errors
        except (paramiko.SSHException, socket.error) as e:
            print(f"{name} ({ip}) ERROR: {e}")


def parse_targets(nodes):
    """
    Resolve target nodes based on user input.

    :param nodes: None, a single node name, or a list of node names.
    """
    # If specific node(s) are requested
    if nodes:
        # Normalize input to a list
        node_list = nodes if isinstance(nodes, (list, tuple, set)) else [nodes]
        
        # Validate each node name (case-insensitive)
        node_map_ci = {k.lower(): k for k in NODE_IP_MAP}
        
        targets = {}

        # Validate each node
        for n in node_list:
            key = n.lower()
            if key not in node_map_ci:
                print(f"Node not valid: {n}, skipping node")
                continue
            
            real_key = node_map_ci[key]
            targets[real_key] = NODE_IP_MAP[real_key]

        if len(targets) == 0:
            print("WARNING: none of the selected node are valid, using full list")
            targets = NODE_IP_MAP

        return targets

    # Return all available targets
    return NODE_IP_MAP

def main():
    # Handle parameters
    # parser = argparse.ArgumentParser(description="Network controller")
    
    # ============= Main loop
    while True:

        # Print available commands
        print("\nAvailable commands:")
        for k in CMD_MAP:
            print(f"  {k} [node1 node2 ...]")
        print("  custom <cmd> [node1 node2 ...]")
        print("  vtysh <cmd> [node1 node2 ...]")
        print("  exit")

        # Get input
        raw = input("CMD > ").strip()
        if not raw:
            continue

        # Exit from main loop
        if raw.lower() == "exit":
            break

        # Separate input
        parts = raw.split()
        nodes = None
        cmd_key = None
        custom_cmd = None

        # Custom command
        if parts[0].lower() == "custom":
            if len(parts) < 2:
                print("custom <cmd> [node1 node2 ...]")
                continue
            # Get node names (last places)
            node_names = [p for p in parts[1:] if p.lower() in {k.lower() for k in NODE_IP_MAP}]
            nodes = node_names if node_names else None
            # Command is everything else
            #  NOTE: only works if node names are spelled right and no command word is the name as a node name
            custom_cmd = " ".join(parts[1:len(parts)-len(node_names)] if nodes else parts[1:])
            execute_cmd(cmd_key=None, targets=parse_targets(nodes), custom_cmd=custom_cmd)

        # Custom vtysh command
        if parts[0].lower() == "vtysh":
            if len(parts) < 2:
                print("vtysh <cmd> [node1 node2 ...]")
                continue
            # Prendi tutti i nodi validi alla fine
            node_names = [p for p in parts[1:] if p.lower() in {k.lower() for k in NODE_IP_MAP}]
            nodes = node_names if node_names else None
            # Command is everything else
            #  NOTE: only works if node names are spelled right and no command word is the name as a node name
            custom_vtysh_cmd = " ".join(parts[1:len(parts)-len(node_names)] if nodes else parts[1:])
            custom_cmd = f"vtysh -c '{custom_vtysh_cmd}'"
            execute_cmd(cmd_key=None, targets=parse_targets(nodes), custom_cmd=custom_cmd)

        else:
            key = " ".join(parts[:2])
            if key not in CMD_MAP:
                print("Command not valid")
                continue
            nodes = parts[2:] if len(parts) > 2 else None
            execute_cmd(cmd_key=key, targets=parse_targets(nodes))



if __name__ == "__main__":
    main()