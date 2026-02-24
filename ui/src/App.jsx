import { useState, useEffect, useRef } from 'react';
import { Phone, Play, Pause, XCircle, Plus, RefreshCw, ChevronRight, Clock, CheckCircle, AlertCircle, Users, Zap, Loader2, Upload, FileText, Activity, TrendingUp, BarChart3 } from 'lucide-react';
import { api } from './api';

const STATUS_COLORS = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  PAUSED: 'bg-orange-100 text-orange-800',
  COMPLETED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  PERMANENTLY_FAILED: 'bg-red-200 text-red-900',
  SCHEDULED: 'bg-purple-100 text-purple-800'
};

function StatusBadge({ status }) {
  return (
    <span className={`px-2 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[status] || 'bg-gray-100'}`}>
      {status}
    </span>
  );
}

function MetricsCard({ title, value, icon: Icon, color, subtitle }) {
  return (
    <div className="bg-white rounded-lg shadow p-4 flex items-center gap-4">
      <div className={`p-3 rounded-full ${color}`}>
        <Icon className="w-6 h-6 text-white" />
      </div>
      <div>
        <p className="text-sm text-gray-500">{title}</p>
        <p className="text-2xl font-bold">{value}</p>
        {subtitle && <p className="text-xs text-gray-400">{subtitle}</p>}
      </div>
    </div>
  );
}

function GlobalMetricsDashboard({ metrics }) {
  if (!metrics) return null;

  return (
    <div className="bg-gradient-to-r from-slate-800 to-slate-900 rounded-lg shadow-lg p-6 mb-6">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <BarChart3 className="w-6 h-6" /> System Overview
        </h2>
        <div className="flex items-center gap-4 text-sm">
          <div className="text-gray-300">
            <span className="text-white font-bold">{metrics.callsPerSecond}</span> calls/sec
          </div>
          <div className="text-gray-300">
            Avg duration: <span className="text-white font-bold">{metrics.avgCallDurationSeconds}s</span>
          </div>
        </div>
      </div>
      
      <div className="grid grid-cols-6 gap-4">
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-gray-400 text-xs uppercase">Campaigns</p>
          <p className="text-2xl font-bold text-white">{metrics.totalCampaigns}</p>
          <p className="text-xs text-green-400">{metrics.activeCampaigns} active</p>
        </div>
        
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-gray-400 text-xs uppercase">Total Calls</p>
          <p className="text-2xl font-bold text-white">{metrics.totalCalls.toLocaleString()}</p>
        </div>
        
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-yellow-400 text-xs uppercase">Pending</p>
          <p className="text-2xl font-bold text-yellow-400">{metrics.pendingCalls.toLocaleString()}</p>
        </div>
        
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-blue-400 text-xs uppercase">In Progress</p>
          <p className="text-2xl font-bold text-blue-400">{metrics.inProgressCalls.toLocaleString()}</p>
        </div>
        
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-green-400 text-xs uppercase">Completed</p>
          <p className="text-2xl font-bold text-green-400">{metrics.completedCalls.toLocaleString()}</p>
        </div>
        
        <div className="bg-slate-700/50 rounded-lg p-4">
          <p className="text-red-400 text-xs uppercase">Failed</p>
          <p className="text-2xl font-bold text-red-400">{(metrics.failedCalls + metrics.permanentlyFailedCalls).toLocaleString()}</p>
          <p className="text-xs text-gray-400">{metrics.totalRetries} retries</p>
        </div>
      </div>

      {/* Worker Thread Pool Utilization */}
      <div className="mt-4 pt-4 border-t border-slate-600">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <Activity className="w-5 h-5 text-blue-400" />
            <span className="text-gray-300">Worker Threads</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-gray-400">
              <span className="text-white font-bold">{metrics.activeWorkerThreads || 0}</span> / {metrics.workerPoolSize || 20} threads
            </span>
            <span className="text-gray-400">
              Queue: <span className="text-cyan-400 font-bold">{metrics.queueDepth || 0}</span>
            </span>
            <span className={`text-lg font-bold ${
              (metrics.workerThreadUtilizationPercent || 0) > 80 ? 'text-red-400' : 
              (metrics.workerThreadUtilizationPercent || 0) > 50 ? 'text-yellow-400' : 'text-green-400'
            }`}>
              {metrics.workerThreadUtilizationPercent || 0}%
            </span>
          </div>
        </div>
        <div className="mt-2 bg-slate-700 rounded-full h-2 overflow-hidden">
          <div 
            className={`h-full transition-all duration-500 ${
              (metrics.workerThreadUtilizationPercent || 0) > 80 ? 'bg-red-500' : 
              (metrics.workerThreadUtilizationPercent || 0) > 50 ? 'bg-yellow-500' : 'bg-blue-500'
            }`}
            style={{ width: `${Math.min(100, metrics.workerThreadUtilizationPercent || 0)}%` }}
          />
        </div>
      </div>

      {/* Concurrency Slot Utilization */}
      <div className="mt-3">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-purple-400" />
            <span className="text-gray-300">Concurrency Slots</span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-gray-400">
              <span className="text-white font-bold">{(metrics.activeConcurrencySlots || 0).toLocaleString()}</span> / {(metrics.totalConcurrencySlots || 0).toLocaleString()} slots
            </span>
            <span className={`text-lg font-bold ${
              (metrics.concurrencyUtilizationPercent || 0) > 80 ? 'text-red-400' : 
              (metrics.concurrencyUtilizationPercent || 0) > 50 ? 'text-yellow-400' : 'text-green-400'
            }`}>
              {metrics.concurrencyUtilizationPercent || 0}%
            </span>
          </div>
        </div>
        <div className="mt-2 bg-slate-700 rounded-full h-2 overflow-hidden">
          <div 
            className={`h-full transition-all duration-500 ${
              (metrics.concurrencyUtilizationPercent || 0) > 80 ? 'bg-red-500' : 
              (metrics.concurrencyUtilizationPercent || 0) > 50 ? 'bg-yellow-500' : 'bg-purple-500'
            }`}
            style={{ width: `${Math.min(100, metrics.concurrencyUtilizationPercent || 0)}%` }}
          />
        </div>
      </div>
    </div>
  );
}

function CampaignList({ campaigns, onSelect, onRefresh, selectedId }) {
  return (
    <div className="bg-white rounded-lg shadow">
      <div className="p-4 border-b flex justify-between items-center">
        <h2 className="text-lg font-semibold">Campaigns</h2>
        <button onClick={onRefresh} className="p-2 hover:bg-gray-100 rounded-full">
          <RefreshCw className="w-4 h-4" />
        </button>
      </div>
      <div className="divide-y max-h-96 overflow-y-auto">
        {campaigns.length === 0 ? (
          <p className="p-4 text-gray-500 text-center">No campaigns yet</p>
        ) : (
          campaigns.map(campaign => (
            <div
              key={campaign.id}
              onClick={() => onSelect(campaign)}
              className={`p-4 cursor-pointer hover:bg-gray-50 flex justify-between items-center ${selectedId === campaign.id ? 'bg-blue-50' : ''}`}
            >
              <div>
                <p className="font-medium">{campaign.name}</p>
                <p className="text-sm text-gray-500">{campaign.metrics?.totalCalls || 0} calls</p>
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge status={campaign.status} />
                <ChevronRight className="w-4 h-4 text-gray-400" />
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

function CampaignDetail({ campaign, onAction, onRefresh }) {
  const [calls, setCalls] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const pageSize = 50;

  useEffect(() => {
    if (campaign) {
      loadCalls();
      // Auto-refresh calls every 15 seconds
      const interval = setInterval(loadCalls, 15000);
      return () => clearInterval(interval);
    }
  }, [campaign?.id, page, statusFilter]);

  const loadCalls = async () => {
    if (!campaign) return;
    setLoading(true);
    try {
      const data = await api.getCampaignCalls(campaign.id, page, pageSize, statusFilter);
      setCalls(data);
    } catch (e) {
      console.error(e);
    }
    setLoading(false);
  };

  const handleRefresh = () => {
    loadCalls();
    onRefresh();
  };

  if (!campaign) {
    return (
      <div className="bg-white rounded-lg shadow p-8 text-center text-gray-500">
        Select a campaign to view details
      </div>
    );
  }

  const metrics = campaign.metrics || {};

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex justify-between items-start mb-4">
          <div>
            <h2 className="text-2xl font-bold">{campaign.name}</h2>
            <p className="text-gray-500">{campaign.description || 'No description'}</p>
          </div>
          <StatusBadge status={campaign.status} />
        </div>

        <div className="grid grid-cols-4 gap-4 mb-6">
          <MetricsCard title="Total Calls" value={metrics.totalCalls || 0} icon={Phone} color="bg-blue-500" />
          <MetricsCard title="Completed" value={metrics.completedCalls || 0} icon={CheckCircle} color="bg-green-500" />
          <MetricsCard title="In Progress" value={metrics.inProgressCalls || 0} icon={Clock} color="bg-yellow-500" />
          <MetricsCard title="Failed" value={metrics.failedCalls || 0} icon={AlertCircle} color="bg-red-500" />
        </div>

        <div className="flex gap-2">
          {campaign.status === 'PENDING' && (
            <button
              onClick={() => onAction('start', campaign.id)}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
            >
              <Play className="w-4 h-4" /> Start Campaign
            </button>
          )}
          {campaign.status === 'IN_PROGRESS' && (
            <button
              onClick={() => onAction('pause', campaign.id)}
              className="flex items-center gap-2 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700"
            >
              <Pause className="w-4 h-4" /> Pause
            </button>
          )}
          {campaign.status === 'PAUSED' && (
            <button
              onClick={() => onAction('start', campaign.id)}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
            >
              <Play className="w-4 h-4" /> Resume
            </button>
          )}
          {['PENDING', 'IN_PROGRESS', 'PAUSED'].includes(campaign.status) && (
            <button
              onClick={() => onAction('cancel', campaign.id)}
              className="flex items-center gap-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
            >
              <XCircle className="w-4 h-4" /> Cancel
            </button>
          )}
          <button
            onClick={handleRefresh}
            className="flex items-center gap-2 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
          >
            <RefreshCw className="w-4 h-4" /> Refresh
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow">
        <div className="p-4 border-b flex justify-between items-center">
          <h3 className="font-semibold">Call Requests</h3>
          <div className="flex items-center gap-3">
            <select 
              value={statusFilter} 
              onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
              className="px-3 py-1 border rounded text-sm"
            >
              <option value="ALL">All Status</option>
              <option value="PENDING">Pending</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="PERMANENTLY_FAILED">Perm. Failed</option>
            </select>
            <button onClick={loadCalls} className="p-2 hover:bg-gray-100 rounded-full">
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Phone</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Retries</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Duration</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Last Attempt</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {loading ? (
                <tr><td colSpan="5" className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
              ) : calls.length === 0 ? (
                <tr><td colSpan="5" className="px-4 py-8 text-center text-gray-500">No calls</td></tr>
              ) : (
                calls.map(call => (
                  <tr key={call.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono">{call.phoneNumber}</td>
                    <td className="px-4 py-3"><StatusBadge status={call.status} /></td>
                    <td className="px-4 py-3">{call.retryCount}</td>
                    <td className="px-4 py-3">{call.callDurationSeconds ? `${call.callDurationSeconds}s` : '-'}</td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {call.lastAttemptedAt ? new Date(call.lastAttemptedAt).toLocaleString() : '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        {/* Pagination */}
        <div className="p-4 border-t flex justify-between items-center">
          <span className="text-sm text-gray-500">
            Showing {calls.length} calls (page {page + 1})
          </span>
          <div className="flex gap-2">
            <button 
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
            >
              Previous
            </button>
            <button 
              onClick={() => setPage(page + 1)}
              disabled={calls.length < pageSize}
              className="px-3 py-1 border rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
            >
              Next
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4">
        <h3 className="font-semibold mb-3">Configuration</h3>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500">Concurrency Limit</p>
            <p className="font-medium">{campaign.concurrencyLimit}</p>
          </div>
          <div>
            <p className="text-gray-500">Priority</p>
            <p className="font-medium">{campaign.priority}</p>
          </div>
          <div>
            <p className="text-gray-500">Max Retries</p>
            <p className="font-medium">{campaign.retryConfig?.maxRetries}</p>
          </div>
          <div>
            <p className="text-gray-500">Callback Timeout</p>
            <p className="font-medium">{campaign.retryConfig?.callbackTimeoutMs / 1000}s</p>
          </div>
          <div>
            <p className="text-gray-500">Business Hours</p>
            <p className="font-medium">
              {campaign.businessHours?.startTime} - {campaign.businessHours?.endTime} ({campaign.businessHours?.timezone})
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function CreateCampaignModal({ isOpen, onClose, onCreate }) {
  const [form, setForm] = useState({
    name: '',
    description: '',
    phoneNumbers: '',
    concurrencyLimit: 10,
    priority: 5,
    maxRetries: 3,
    callbackTimeoutMs: 120000,
    // Business hours
    enableBusinessHours: false,
    startTime: '09:00',
    endTime: '18:00',
    timezone: 'UTC',
    allowedDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
  });
  const [inputMode, setInputMode] = useState('file'); // 'file' or 'text'
  const [selectedFile, setSelectedFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef(null);

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      let phoneNumbers = [];
      
      if (inputMode === 'file' && selectedFile) {
        // Read file content
        const text = await selectedFile.text();
        phoneNumbers = text.split('\n').map(p => p.trim()).filter(p => p.length > 0);
      } else if (inputMode === 'text') {
        phoneNumbers = form.phoneNumbers.split('\n').map(p => p.trim()).filter(p => p.length > 0);
      }

      if (phoneNumbers.length === 0) {
        alert('Please provide at least one phone number');
        setLoading(false);
        return;
      }

      const payload = {
        name: form.name,
        description: form.description,
        phoneNumbers,
        concurrencyLimit: parseInt(form.concurrencyLimit),
        priority: parseInt(form.priority),
        retryConfig: {
          maxRetries: parseInt(form.maxRetries),
          callbackTimeoutMs: parseInt(form.callbackTimeoutMs)
        }
      };
      
      // Add business hours if enabled
      if (form.enableBusinessHours) {
        payload.businessHours = {
          startTime: form.startTime,
          endTime: form.endTime,
          timezone: form.timezone,
          allowedDays: form.allowedDays.join(',')
        };
      }
      
      await onCreate(payload);
      onClose();
      setForm({ 
        name: '', description: '', phoneNumbers: '', concurrencyLimit: 10, priority: 5, 
        maxRetries: 3, callbackTimeoutMs: 120000, enableBusinessHours: false,
        startTime: '09:00', endTime: '18:00', timezone: 'UTC',
        allowedDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
      });
      setSelectedFile(null);
    } catch (e) {
      alert('Failed to create campaign: ' + e.message);
    }
    setLoading(false);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="p-6 border-b">
          <h2 className="text-xl font-bold">Create Campaign</h2>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Campaign Name *</label>
            <input
              type="text"
              required
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Q1 Marketing Campaign"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Description</label>
            <input
              type="text"
              value={form.description}
              onChange={e => setForm({ ...form, description: e.target.value })}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="Optional description"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Concurrency Limit</label>
              <input
                type="number"
                min="1"
                max="100"
                value={form.concurrencyLimit}
                onChange={e => setForm({ ...form, concurrencyLimit: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Priority (1-10)</label>
              <input
                type="number"
                min="1"
                max="10"
                value={form.priority}
                onChange={e => setForm({ ...form, priority: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Max Retries</label>
              <input
                type="number"
                min="0"
                max="10"
                value={form.maxRetries}
                onChange={e => setForm({ ...form, maxRetries: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Callback Timeout (ms)</label>
              <input
                type="number"
                min="30000"
                max="600000"
                value={form.callbackTimeoutMs}
                onChange={e => setForm({ ...form, callbackTimeoutMs: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          {/* Business Hours Section */}
          <div className="border rounded-lg p-4 bg-gray-50">
            <div className="flex items-center gap-2 mb-3">
              <input
                type="checkbox"
                id="enableBusinessHours"
                checked={form.enableBusinessHours}
                onChange={e => setForm({ ...form, enableBusinessHours: e.target.checked })}
                className="w-4 h-4 text-blue-600 rounded"
              />
              <label htmlFor="enableBusinessHours" className="text-sm font-medium">
                Restrict to Business Hours
              </label>
            </div>
            
            {form.enableBusinessHours && (
              <div className="space-y-3 mt-3 pt-3 border-t">
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label className="block text-xs font-medium mb-1">Start Time</label>
                    <input
                      type="time"
                      value={form.startTime}
                      onChange={e => setForm({ ...form, startTime: e.target.value })}
                      className="w-full px-2 py-1 border rounded text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1">End Time</label>
                    <input
                      type="time"
                      value={form.endTime}
                      onChange={e => setForm({ ...form, endTime: e.target.value })}
                      className="w-full px-2 py-1 border rounded text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1">Timezone</label>
                    <select
                      value={form.timezone}
                      onChange={e => setForm({ ...form, timezone: e.target.value })}
                      className="w-full px-2 py-1 border rounded text-sm"
                    >
                      <option value="UTC">UTC</option>
                      <option value="America/New_York">US Eastern</option>
                      <option value="America/Chicago">US Central</option>
                      <option value="America/Denver">US Mountain</option>
                      <option value="America/Los_Angeles">US Pacific</option>
                      <option value="Europe/London">UK</option>
                      <option value="Europe/Paris">Central Europe</option>
                      <option value="Asia/Kolkata">India</option>
                      <option value="Asia/Tokyo">Japan</option>
                      <option value="Australia/Sydney">Australia</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium mb-1">Allowed Days</label>
                  <div className="flex flex-wrap gap-2">
                    {['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => (
                      <label key={day} className="flex items-center gap-1 text-xs">
                        <input
                          type="checkbox"
                          checked={form.allowedDays.includes(day)}
                          onChange={e => {
                            if (e.target.checked) {
                              setForm({ ...form, allowedDays: [...form.allowedDays, day] });
                            } else {
                              setForm({ ...form, allowedDays: form.allowedDays.filter(d => d !== day) });
                            }
                          }}
                          className="w-3 h-3"
                        />
                        {day.slice(0, 3)}
                      </label>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-2">Phone Numbers *</label>
            <div className="flex gap-2 mb-3">
              <button
                type="button"
                onClick={() => setInputMode('file')}
                className={`flex-1 py-2 px-3 rounded-lg border flex items-center justify-center gap-2 ${inputMode === 'file' ? 'bg-blue-50 border-blue-500 text-blue-700' : 'hover:bg-gray-50'}`}
              >
                <Upload className="w-4 h-4" /> Upload File
              </button>
              <button
                type="button"
                onClick={() => setInputMode('text')}
                className={`flex-1 py-2 px-3 rounded-lg border flex items-center justify-center gap-2 ${inputMode === 'text' ? 'bg-blue-50 border-blue-500 text-blue-700' : 'hover:bg-gray-50'}`}
              >
                <FileText className="w-4 h-4" /> Manual Entry
              </button>
            </div>

            {inputMode === 'file' ? (
              <div 
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed rounded-lg p-6 text-center cursor-pointer hover:border-blue-500 hover:bg-blue-50 transition-colors"
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".txt,.csv"
                  onChange={handleFileSelect}
                  className="hidden"
                />
                {selectedFile ? (
                  <div className="text-green-600">
                    <FileText className="w-8 h-8 mx-auto mb-2" />
                    <p className="font-medium">{selectedFile.name}</p>
                    <p className="text-sm text-gray-500">{(selectedFile.size / 1024).toFixed(1)} KB</p>
                  </div>
                ) : (
                  <div className="text-gray-500">
                    <Upload className="w-8 h-8 mx-auto mb-2" />
                    <p>Click to upload CSV or TXT file</p>
                    <p className="text-xs mt-1">One phone number per line</p>
                  </div>
                )}
              </div>
            ) : (
              <textarea
                rows={5}
                value={form.phoneNumbers}
                onChange={e => setForm({ ...form, phoneNumbers: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 font-mono text-sm"
                placeholder="+1234567890&#10;+0987654321&#10;+1122334455"
              />
            )}
            <p className="text-xs text-gray-500 mt-1">
              For large imports (100K+), use file upload for better performance
            </p>
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? 'Creating...' : 'Create Campaign'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function SingleCallPanel() {
  const [phone, setPhone] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleCall = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    try {
      const data = await api.triggerCall(phone);
      setResult(data);
    } catch (e) {
      setResult({ error: e.message });
    }
    setLoading(false);
  };

  return (
    <div className="bg-white rounded-lg shadow p-4">
      <h3 className="font-semibold mb-3">Quick Call</h3>
      <form onSubmit={handleCall} className="flex gap-2">
        <input
          type="tel"
          value={phone}
          onChange={e => setPhone(e.target.value)}
          placeholder="+1234567890"
          className="flex-1 px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          disabled={loading || !phone}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
        >
          <Phone className="w-4 h-4" />
          {loading ? 'Calling...' : 'Call'}
        </button>
      </form>
      {result && (
        <div className={`mt-3 p-3 rounded-lg ${result.error ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'}`}>
          {result.error ? result.error : `Call initiated: ${result.id} - ${result.status}`}
        </div>
      )}
    </div>
  );
}

function SimulationModal({ isOpen, onClose, onStart }) {
  const [config, setConfig] = useState({
    numCampaigns: 5,
    callsPerCampaign: 100,
    concurrencyLimit: 20,
    autoStart: true
  });
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState({ current: 0, total: 0, status: '' });

  const generatePhoneNumbers = (count) => {
    const phones = [];
    for (let i = 0; i < count; i++) {
      phones.push(`+1${Math.floor(1000000000 + Math.random() * 9000000000)}`);
    }
    return phones;
  };

  const runSimulation = async () => {
    setRunning(true);
    const total = config.numCampaigns;
    const campaignIds = [];

    for (let i = 0; i < config.numCampaigns; i++) {
      setProgress({ current: i + 1, total, status: `Creating campaign ${i + 1}/${total}...` });
      
      try {
        const phones = generatePhoneNumbers(config.callsPerCampaign);
        const campaign = await api.createCampaign({
          name: `Simulation Campaign ${i + 1}`,
          description: `Auto-generated with ${config.callsPerCampaign} calls`,
          phoneNumbers: phones,
          concurrencyLimit: config.concurrencyLimit,
          priority: Math.floor(Math.random() * 10) + 1,
          retryConfig: {
            maxRetries: 3,
            syncInitialBackoffMs: 1000,
            syncBackoffMultiplier: 2.0,
            callbackRetryDelayMs: 30000,
            callbackTimeoutMs: 600000
          }
        });
        campaignIds.push(campaign.id);
      } catch (e) {
        console.error('Failed to create campaign:', e);
      }
    }

    if (config.autoStart) {
      setProgress({ current: 0, total: campaignIds.length, status: 'Starting campaigns...' });
      for (let i = 0; i < campaignIds.length; i++) {
        setProgress({ current: i + 1, total: campaignIds.length, status: `Starting campaign ${i + 1}/${campaignIds.length}...` });
        try {
          await api.startCampaign(campaignIds[i]);
        } catch (e) {
          console.error('Failed to start campaign:', e);
        }
      }
    }

    setProgress({ current: total, total, status: 'Simulation started!' });
    setRunning(false);
    setTimeout(() => {
      onStart();
      onClose();
    }, 1000);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md">
        <div className="p-6 border-b bg-gradient-to-r from-purple-600 to-blue-600 rounded-t-lg">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Zap className="w-6 h-6" /> Load Simulation
          </h2>
          <p className="text-purple-100 text-sm mt-1">Generate bulk campaigns to test the system</p>
        </div>
        
        <div className="p-6 space-y-4">
          {running ? (
            <div className="text-center py-8">
              <Loader2 className="w-12 h-12 text-blue-600 animate-spin mx-auto mb-4" />
              <p className="text-lg font-medium">{progress.status}</p>
              <div className="mt-4 bg-gray-200 rounded-full h-2 w-full">
                <div 
                  className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                  style={{ width: `${(progress.current / progress.total) * 100}%` }}
                />
              </div>
              <p className="text-sm text-gray-500 mt-2">{progress.current} / {progress.total}</p>
            </div>
          ) : (
            <>
              <div>
                <label className="block text-sm font-medium mb-1">Number of Campaigns</label>
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={config.numCampaigns}
                  onChange={e => setConfig({ ...config, numCampaigns: parseInt(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Calls per Campaign</label>
                <input
                  type="number"
                  min="10"
                  max="100000"
                  value={config.callsPerCampaign}
                  onChange={e => setConfig({ ...config, callsPerCampaign: parseInt(e.target.value) || 10 })}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Total calls: {(config.numCampaigns * config.callsPerCampaign).toLocaleString()}
                </p>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Concurrency Limit per Campaign</label>
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={config.concurrencyLimit}
                  onChange={e => setConfig({ ...config, concurrencyLimit: parseInt(e.target.value) || 1 })}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-purple-500"
                />
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="autoStart"
                  checked={config.autoStart}
                  onChange={e => setConfig({ ...config, autoStart: e.target.checked })}
                  className="w-4 h-4 text-purple-600 rounded"
                />
                <label htmlFor="autoStart" className="text-sm">Auto-start campaigns after creation</label>
              </div>

              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-sm">
                <p className="font-medium text-yellow-800">Simulation Info:</p>
                <ul className="text-yellow-700 mt-1 space-y-1">
                  <li>• Mock telephony: 3-10s call duration</li>
                  <li>• 5% failed callback (NO_ANSWER, BUSY, etc.)</li>
                  <li>• 1% no callback (timeout after 10 min)</li>
                  <li>• 0.5% sync failure (network error)</li>
                  <li>• 24/7 calling (no business hours)</li>
                  <li>• Auto-retry with exponential backoff</li>
                </ul>
              </div>
            </>
          )}
        </div>

        <div className="p-4 border-t flex justify-end gap-2">
          <button
            onClick={onClose}
            disabled={running}
            className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            onClick={runSimulation}
            disabled={running}
            className="px-4 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white rounded-lg hover:from-purple-700 hover:to-blue-700 disabled:opacity-50 flex items-center gap-2"
          >
            <Zap className="w-4 h-4" />
            {running ? 'Running...' : 'Start Simulation'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [campaigns, setCampaigns] = useState([]);
  const [selectedCampaign, setSelectedCampaign] = useState(null);
  const [showCreate, setShowCreate] = useState(false);
  const [showSimulation, setShowSimulation] = useState(false);
  const [loading, setLoading] = useState(true);
  const [globalMetrics, setGlobalMetrics] = useState(null);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 15000); // Refresh every 15 seconds
    return () => clearInterval(interval);
  }, []);

  const loadData = async () => {
    try {
      const [campaignsData, metricsData] = await Promise.all([
        api.getCampaigns(),
        api.getGlobalMetrics()
      ]);
      setCampaigns(campaignsData);
      setGlobalMetrics(metricsData);
      if (selectedCampaign) {
        const updated = campaignsData.find(c => c.id === selectedCampaign.id);
        if (updated) setSelectedCampaign(updated);
      }
    } catch (e) {
      console.error('Failed to load data:', e);
    }
    setLoading(false);
  };

  const loadCampaigns = loadData;

  const handleAction = async (action, id) => {
    try {
      if (action === 'start') await api.startCampaign(id);
      else if (action === 'pause') await api.pauseCampaign(id);
      else if (action === 'cancel') await api.cancelCampaign(id);
      loadCampaigns();
    } catch (e) {
      alert('Action failed: ' + e.message);
    }
  };

  const handleCreate = async (data) => {
    await api.createCampaign(data);
    loadCampaigns();
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <Phone className="w-8 h-8 text-blue-600" />
            <h1 className="text-xl font-bold">Voice Campaign Manager</h1>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setShowSimulation(true)}
              className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-purple-600 to-blue-600 text-white rounded-lg hover:from-purple-700 hover:to-blue-700"
            >
              <Zap className="w-4 h-4" /> Simulate
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <Plus className="w-4 h-4" /> New Campaign
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 py-6">
        <GlobalMetricsDashboard metrics={globalMetrics} />
        
        <div className="grid grid-cols-3 gap-6">
          <div className="space-y-4">
            <SingleCallPanel />
            <CampaignList
              campaigns={campaigns}
              onSelect={setSelectedCampaign}
              onRefresh={loadCampaigns}
              selectedId={selectedCampaign?.id}
            />
          </div>
          <div className="col-span-2">
            <CampaignDetail
              campaign={selectedCampaign}
              onAction={handleAction}
              onRefresh={loadCampaigns}
            />
          </div>
        </div>
      </main>

      <CreateCampaignModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onCreate={handleCreate}
      />

      <SimulationModal
        isOpen={showSimulation}
        onClose={() => setShowSimulation(false)}
        onStart={loadCampaigns}
      />
    </div>
  );
}
