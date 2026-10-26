import glob
import ipaddress
import json
import os
import re
import time
import paramiko

STARTUP_DIR = "../startup"

host_configs = {}
for filepath in glob.glob(os.path.join(STARTUP_DIR, "*.conf")):
    with open(filepath, "r") as f:
        text = f.read()
        m_host = re.search(r"hostname\s+(\S+)", text)
        if m_host:
            host_configs[m_host.group(1)] = text

# Estrazione AS interno (preso da PE1)
my_as_match = re.search(r"router bgp\s+(\d+)", host_configs.get("PE1", ""))
MY_AS = int(my_as_match.group(1)) if my_as_match else 65020

USERNAME = "root"
PASSWORD = "admin"
INTERVAL = 180
LOCAL_PREF = 200
MED_PREFERRED = 50
MED_BACKUP = 200


def get_loopback_ip(conf_text):
    m = re.search(r"interface lo\s+ip address\s+([\d.]+)/\d+", conf_text)
    return m.group(1) if m else None


def get_core_ip(conf_text):
    m = re.search(
        r"interface\s+\w+\s+ip address\s+([\d.]+)/\d+\s+description to_CORE",
        conf_text,
    )
    return m.group(1) if m else None


def get_upstream_ip(conf_text):
    bgp_section = re.search(
        r"router bgp \d+(.*?)(?:\n\w|\Z)", conf_text, re.DOTALL
    )
    if bgp_section:
        neighbors = re.findall(
            r"neighbor\s+([\d.]+)\s+remote-as\s+(\d+)", bgp_section.group(1)
        )
        for ip, asn in neighbors:
            if int(asn) != MY_AS:
                return ip
    return None


def get_ce_neighbors(pe_conf_text):
    bgp_section = re.search(
        r"router bgp \d+(.*?)(?:\n\w|\Z)", pe_conf_text, re.DOTALL
    )
    ces = []
    if bgp_section:
        neighbors = re.findall(
            r"neighbor\s+([\d.]+)\s+remote-as\s+(\d+)", bgp_section.group(1)
        )
        for ip, asn in neighbors:
            if int(asn) != MY_AS:
                ces.append(ip)
    return ces


# Estrazione IP dei router
PE1_IP = get_loopback_ip(host_configs.get("PE1", ""))
PE2_IP = get_loopback_ip(host_configs.get("PE2", ""))
GW1_IP = get_loopback_ip(host_configs.get("GW1", ""))
GW2_IP = get_loopback_ip(host_configs.get("GW2", ""))

ALL_PEERS = [PE1_IP, PE2_IP, GW1_IP, GW2_IP]

# Mappatura loopback -> IP core
lo_ip_map = {
    PE1_IP: get_core_ip(host_configs.get("PE1", "")),
    PE2_IP: get_core_ip(host_configs.get("PE2", "")),
    GW1_IP: get_core_ip(host_configs.get("GW1", "")),
    GW2_IP: get_core_ip(host_configs.get("GW2", "")),
}

# IP degli upstream router
UP1_IP = get_upstream_ip(host_configs.get("GW1", ""))
UP2_IP = get_upstream_ip(host_configs.get("GW2", ""))

# Estrazione reti clienti (CU_NETS) dai file CE.conf
CU_NETS = set()
for hname, text in host_configs.items():
    if hname.startswith("CE"):
        m_if = re.search(
            r"interface\s+\w+\s+ip address\s+([\d.]+)/(\d+)\s+description to_Private",
            text,
        )
        if m_if:
            ip, prefix_len = m_if.groups()
            net = ipaddress.ip_interface(f"{ip}/{prefix_len}")
            CU_NETS.add(str(net.network))

# Vicini CE per ciascun PE
CE_NEIGHBORS = {
    PE1_IP: get_ce_neighbors(host_configs.get("PE1", "")),
    PE2_IP: get_ce_neighbors(host_configs.get("PE2", "")),
}


def run_vtysh(ssh, cmds):
    """Run a list of vtysh commands in a single call and wait for completion."""
    vtysh_cmd = "vtysh \\\n" + " \\\n".join(f'    -c "{c}"' for c in cmds)
    print(vtysh_cmd)
    stdin, stdout, stderr = ssh.exec_command(vtysh_cmd)
    stdout.channel.recv_exit_status()
    out = stdout.read().decode()
    err = stderr.read().decode()
    if out:
        print(f"OUT: {out}")
    if err:
        print(f"ERR: {err}")


def open_ssh(router_ip):
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(lo_ip_map[router_ip], username=USERNAME, password=PASSWORD)
    return ssh


def configure_bgp_local_pref(gw_ip, route_map_name, bucket):
    """GW -> upstream: set local-pref 200 (inbound from the upstream) on the

    to-internet prefixes in 'bucket', so that the whole AS prefers this GW
    for those destinations.
    """
    my_up = UP1_IP if gw_ip == GW1_IP else UP2_IP

    cmds = ["configure terminal"]
    cmds.append(f"no route-map {route_map_name}")

    seq = 10
    for prefix in bucket:
        pl_name = f'PL-{prefix.replace("/", "_")}'
        cmds.append(f"ip prefix-list {pl_name} permit {prefix}")
        cmds.append(f"route-map {route_map_name} permit {seq}")
        cmds.append(f" match ip address prefix-list {pl_name}")
        cmds.append(f" set local-preference {LOCAL_PREF}")
        seq += 10

    cmds.append(f"route-map {route_map_name} permit 999")
    cmds.append(f"router bgp {MY_AS}")
    cmds.append(f" neighbor {my_up} route-map {route_map_name} in")
    cmds.append("end")

    print(f"Executing the following command in GW {gw_ip}:")

    ssh = open_ssh(gw_ip)
    try:
        run_vtysh(ssh, cmds)
        for peer_ip in ALL_PEERS:
            if peer_ip != gw_ip:
                run_vtysh(
                    ssh,
                    [
                        f"clear ip bgp {peer_ip} soft in",
                        f"clear ip bgp {peer_ip} soft out",
                    ],
                )
        run_vtysh(
            ssh,
            [
                f"clear ip bgp {my_up} soft in",
                f"clear ip bgp {my_up} soft out",
            ],
        )
    finally:
        ssh.close()


def configure_bgp_med(pe_ip, route_map_name, preferred, backup):
    """CE -> PE: outbound route-map applied by the PE towards the CEs that sets the

    MED on the internet prefixes: low MED for the prefixes in 'preferred'
    (the CE will pick this PE), high MED for the prefixes in 'backup'
    (the CE will pick the other PE).
    """
    cmds = ["configure terminal"]
    cmds.append(f"no route-map {route_map_name}")

    seq = 10
    for prefix, med in [(p, MED_PREFERRED) for p in preferred] + [
        (p, MED_BACKUP) for p in backup
    ]:
        pl_name = f'PL-{prefix.replace("/", "_")}'
        cmds.append(f"ip prefix-list {pl_name} permit {prefix}")
        cmds.append(f"route-map {route_map_name} permit {seq}")
        cmds.append(f" match ip address prefix-list {pl_name}")
        cmds.append(f" set metric {med}")
        seq += 10

    cmds.append(f"route-map {route_map_name} permit 999")
    cmds.append(f"router bgp {MY_AS}")
    for ce_ip in CE_NEIGHBORS[pe_ip]:
        cmds.append(f" neighbor {ce_ip} route-map {route_map_name} out")
    cmds.append("end")

    print(f"Executing the following command in PE {pe_ip}:")

    ssh = open_ssh(pe_ip)
    try:
        run_vtysh(ssh, cmds)
        for ce_ip in CE_NEIGHBORS[pe_ip]:
            run_vtysh(ssh, [f"clear ip bgp {ce_ip} soft out"])
    finally:
        ssh.close()


with open("traffic-matrices.json", "r") as f:
    traffic_matrices = json.load(f)

matrix_index = 0
time.sleep(30)
iter = 1

while True:
    current_matrix = traffic_matrices[matrix_index]

    print(
        f"\n################################__________\nCONFIGURING BGP PARAMETERS ITER {iter}\n################################__________\n"
    )

    bucket1 = []
    bucket2 = []
    total1 = 0
    total2 = 0
    assigned_destinations = set()

    for entry in current_matrix:
        if entry["from"] not in CU_NETS:
            continue

        dst = entry["to"]
        traffic = entry["expected-traffic"]

        if dst in assigned_destinations:
            if dst in bucket1:
                total1 += traffic
            else:
                total2 += traffic
        else:
            if total1 <= total2:
                total1 += traffic
                bucket1.append(dst)
            else:
                total2 += traffic
                bucket2.append(dst)
            assigned_destinations.add(dst)

    print(f"Bucket1: {bucket1}, total1: {total1}")
    print(f"Bucket2: {bucket2}, total2: {total2}")

    configure_bgp_med(
        pe_ip=PE1_IP,
        route_map_name="SET-MED-PE1",
        preferred=bucket1,
        backup=bucket2,
    )

    configure_bgp_med(
        pe_ip=PE2_IP,
        route_map_name="SET-MED-PE2",
        preferred=bucket2,
        backup=bucket1,
    )

    configure_bgp_local_pref(
        gw_ip=GW1_IP,
        route_map_name="SET-LP-GW1",
        bucket=bucket1,
    )

    configure_bgp_local_pref(
        gw_ip=GW2_IP,
        route_map_name="SET-LP-GW2",
        bucket=bucket2,
    )

    matrix_index = (matrix_index + 1) % len(traffic_matrices)
    time.sleep(INTERVAL)
    iter += 1
    