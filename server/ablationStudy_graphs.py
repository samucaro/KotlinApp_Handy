import matplotlib.pyplot as plt
import numpy as np

# Definizione delle etichette (4 Livelli)
labels = ['L0\nPlaintext', 'L1\nBlur', 'L2\nPaillier', 'L3\nFull Protocol']
x = np.arange(len(labels))
width = 0.35

# ==========================================
# DATI: HEARTBEAT
# ==========================================
hb_time_1024 = [0.0002, 0.0012, 4.0874, 1.8128]
hb_std_1024  = [0.0001, 0.0004, 4.4731, 0.7164]
hb_time_2048 = [0.0002, 0.0011, 31.6800, 15.8221]
hb_std_2048  = [0.0002, 0.0002, 0.0817, 0.0589]
hb_payload_1024 = [16, 16, 510, 270]
hb_payload_2048 = [16, 16, 1022, 527]

# ==========================================
# DATI: HELP-REQUEST
# ==========================================
hr_time_1024 = [0.0001, 0.0005, 4.5709, 3.0352]
hr_std_1024  = [0.0001, 0.0001, 0.0444, 0.0297]
hr_time_2048 = [0.0002, 0.0007, 47.5093, 34.5918]
hr_std_2048  = [0.0001, 0.0002, 0.1335, 8.3360]
hr_payload_1024 = [24, 24, 765, 526]
hr_payload_2048 = [24, 24, 1533, 1038]

# ==========================================
# DATI: MATCH
# ==========================================
ma_time_1024 = [0.0012, 0.0034, 9.0616, 8.1755]
ma_std_1024  = [0.0022, 0.0024, 0.0337, 0.5844]
ma_time_2048 = [0.0018, 0.0042, 94.7384, 63.3481]
ma_std_2048  = [0.0035, 0.0116, 0.1373, 0.2850]
ma_payload_1024 = [0, 0, 0, 0]
ma_payload_2048 = [0, 0, 0, 0]

def plot_phase(phase_name, time_1024, std_1024, time_2048, std_2048, payload_1024, payload_2048, filename):
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    
    # Colori identici all'immagine (senza pattern a strisce)
    color_time_1024 = '#3498db'  # Blu
    color_time_2048 = '#e74c3c'  # Rosso
    color_pay_1024  = '#2ecc71'  # Verde
    color_pay_2048  = '#f39c12'  # Arancione
    
    # --- Grafico 1: Tempo (Latenza Computazionale) ---
    rects1 = ax1.bar(x - width/2, time_1024, width, yerr=std_1024, capsize=4, label='1024-bit', color=color_time_1024, error_kw=dict(lw=1.5, capthick=1.5))
    rects2 = ax1.bar(x + width/2, time_2048, width, yerr=std_2048, capsize=4, label='2048-bit', color=color_time_2048, error_kw=dict(lw=1.5, capthick=1.5))
    
    ax1.set_ylabel('Tempo (ms)', fontweight='bold', fontsize=12)
    ax1.set_title(f'Latenza Computazionale - {phase_name}', fontweight='bold', fontsize=15)
    ax1.set_xticks(x)
    ax1.set_xticklabels(labels, fontsize=11)
    ax1.legend()
    ax1.grid(axis='y', linestyle='--', alpha=0.7)
    
    # Calcola il limite Y in base all'astina più alta
    max_y = max(max([m+s for m,s in zip(time_1024, std_1024)]), max([m+s for m,s in zip(time_2048, std_2048)]))
    ax1.set_ylim(0, max_y * 1.15)
    
    # Etichette sopra le barre (Orizzontali)
    # Mostra 4 cifre decimali se il valore è < 1 (es. 0.0012), altrimenti 2 cifre (es. 45.94)
    for i, r in enumerate(rects1):
        h = r.get_height()
        label_text = f'{h:.4f}' if h < 1 else f'{h:.2f}'
        ax1.text(r.get_x() + r.get_width()/2., h + std_1024[i] + (max_y*0.02), label_text, ha='center', va='bottom', fontsize=10)
        
    for i, r in enumerate(rects2):
        h = r.get_height()
        label_text = f'{h:.4f}' if h < 1 else f'{h:.2f}'
        ax1.text(r.get_x() + r.get_width()/2., h + std_2048[i] + (max_y*0.02), label_text, ha='center', va='bottom', fontsize=10)
        
    # --- Grafico 2: Dimensione (Overhead Payload) ---
    rects3 = ax2.bar(x - width/2, payload_1024, width, label='1024-bit', color=color_pay_1024)
    rects4 = ax2.bar(x + width/2, payload_2048, width, label='2048-bit', color=color_pay_2048)
    
    ax2.set_ylabel('Payload (Bytes)', fontweight='bold', fontsize=12)
    ax2.set_title(f'Overhead di Rete - {phase_name}', fontweight='bold', fontsize=15)
    ax2.set_xticks(x)
    ax2.set_xticklabels(labels, fontsize=11)
    ax2.legend()
    ax2.grid(axis='y', linestyle='--', alpha=0.7)
    
    max_p = max(max(payload_1024), max(payload_2048))
    if max_p > 0:
        ax2.set_ylim(0, max_p * 1.15)
    else:
        # Per la fase di Match dove tutto è 0, mantiene l'asse visivamente centrato
        ax2.set_ylim(-0.05, 0.05)
    
    # Etichette sopra le barre (Payload, senza decimali)
    for r in rects3 + rects4:
        h = r.get_height()
        offset = (max_p * 0.02) if max_p > 0 else 0.005
        ax2.text(r.get_x() + r.get_width()/2., h + offset, f'{int(h)}', ha='center', va='bottom', fontsize=10)
        
    plt.tight_layout()
    plt.savefig(filename, dpi=300)
    print(f"Grafico salvato: {filename}")
    plt.close()

# Esecuzione e salvataggio
plot_phase('Heartbeat', hb_time_1024, hb_std_1024, hb_time_2048, hb_std_2048, hb_payload_1024, hb_payload_2048, 'Heartbeat_Analysis_V2.png')
plot_phase('Help-Request', hr_time_1024, hr_std_1024, hr_time_2048, hr_std_2048, hr_payload_1024, hr_payload_2048, 'HelpRequest_Analysis_V2.png')
plot_phase('Match', ma_time_1024, ma_std_1024, ma_time_2048, ma_std_2048, ma_payload_1024, ma_payload_2048, 'Match_Analysis_V2.png')