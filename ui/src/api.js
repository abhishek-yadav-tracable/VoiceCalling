const API_BASE = '/api/v1';

export const api = {
  async getGlobalMetrics() {
    const res = await fetch(`${API_BASE}/metrics`);
    if (!res.ok) throw new Error('Failed to fetch metrics');
    return res.json();
  },

  async getCampaigns() {
    const res = await fetch(`${API_BASE}/campaigns`);
    if (!res.ok) throw new Error('Failed to fetch campaigns');
    return res.json();
  },

  async getCampaign(id) {
    const res = await fetch(`${API_BASE}/campaigns/${id}`);
    if (!res.ok) throw new Error('Failed to fetch campaign');
    return res.json();
  },

  async createCampaign(data) {
    const res = await fetch(`${API_BASE}/campaigns`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error('Failed to create campaign');
    return res.json();
  },

  async importPhoneNumbers(campaignId, file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/campaigns/${campaignId}/import`, {
      method: 'POST',
      body: formData
    });
    if (!res.ok) throw new Error('Failed to import phone numbers');
    return res.json();
  },

  async importPhoneNumbersBatch(campaignId, phoneNumbers) {
    const res = await fetch(`${API_BASE}/campaigns/${campaignId}/import/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumbers })
    });
    if (!res.ok) throw new Error('Failed to import phone numbers');
    return res.json();
  },

  async startCampaign(id) {
    const res = await fetch(`${API_BASE}/campaigns/${id}/start`, { method: 'POST' });
    if (!res.ok) throw new Error('Failed to start campaign');
    return res.json();
  },

  async pauseCampaign(id) {
    const res = await fetch(`${API_BASE}/campaigns/${id}/pause`, { method: 'POST' });
    if (!res.ok) throw new Error('Failed to pause campaign');
    return res.json();
  },

  async cancelCampaign(id) {
    const res = await fetch(`${API_BASE}/campaigns/${id}/cancel`, { method: 'POST' });
    if (!res.ok) throw new Error('Failed to cancel campaign');
    return res.json();
  },

  async getCampaignCalls(id, page = 0, size = 50, status = 'ALL') {
    const params = new URLSearchParams({ page, size });
    if (status && status !== 'ALL') {
      params.append('status', status);
    }
    const res = await fetch(`${API_BASE}/campaigns/${id}/calls?${params}`);
    if (!res.ok) throw new Error('Failed to fetch calls');
    return res.json();
  },

  async triggerCall(phoneNumber) {
    const res = await fetch(`${API_BASE}/calls`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phoneNumber })
    });
    if (!res.ok) throw new Error('Failed to trigger call');
    return res.json();
  },

  async getCallStatus(id) {
    const res = await fetch(`${API_BASE}/calls/${id}`);
    if (!res.ok) throw new Error('Failed to fetch call status');
    return res.json();
  }
};
