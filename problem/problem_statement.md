# Programming Assignment: Outbound Voice Campaign Microservice

## Problem Overview

You are tasked with creating and enhancing microservice that provides:

1. An API to trigger an outbound voice call, and
2. An API to check the status of a call (in_progress, completed, or failed).
3. Extend this system to support outbound call campaigns
   — allowing multiple calls to be triggered and managed together under a single campaign.

### Requirements:

The enhanced system should enable management of outbound call campaigns, where
multiple calls can be triggered and monitored together under a single campaign.

The following capabilities are expected to be supported:

1. Campaign Management

- The system should allow clients to create and manage outbound call campaigns.
- Each campaign should contain a group of phone numbers that need to be called.
- Campaigns should maintain their own lifecycle and track the progress and results of all
  calls within them.

2. Business Hour Scheduling

- Campaigns should support defining specific business hours during which calls can be
  made.
- Calls should be automatically scheduled and executed only within these configured time
  windows.
- The system should be able to handle different time zones and prevent calls from being
  triggered outside allowed hours.

3. Concurrency Control

- The system should allow configuration of maximum concurrent calls that can be active
  at a time within a campaign.
- If no value is specified, the system should apply a default concurrency limit.

4. Retry Handling

- If a call attempt fails, the system should automatically retry the call based on the
  campaign’s retry configuration.
- The system should ensure that failed calls are retried before new calls are initiated,
  maintaining fairness and efficiency.

5. Status Tracking

- The system should track and expose the status of individual calls (e.g., in-progress,
  completed, failed) and the overall campaign status (e.g., pending, in-progress,
  completed, failed).
- Campaigns should provide aggregated statistics such as total calls, calls completed,
  calls failed, and retries attempted.

### System Design Requirements:

Apart from the code that implements the above requirements, you must include a high-level
architecture diagram or text-based explanation describing:

- How APIs, databases, and background workers interact to meet the above capabilities
- The choice of technology for these external components (e.g., message queues like
  RabbitMQ, databases like PostgreSQL, task schedulers like Celery, etc.)
- How the system ensures fault tolerance and scalability.

Implementation Expectations

- Use Java
- Code must be production-quality, readable, and modular.
- Include clear instructions for how to:
    - Set up and run the service locally
    - Run sample tests or curl commands
- Include mock implementations for outbound call triggering (no need for real telephony
  integration).

Deliverables
Candidates should submit:

- Source code (in a Git repo or zip file)
- README file with:
    - Setup instructions
    - Example API usage
    - System design explanation
- (Optional) Diagrams or flowcharts for the system design

### Discussion Notes:

Allow for random failures at various interfaces in the system

Implement proper rate limiting, throttling (circuit breaker), routing logic (optional 3rd party
routing)
One campaign should not block another campaign. ensure fairness.

System needs to be efficient. Calls may end at any time say within a couple of seconds to minutes,
Do not wait for calls to complete.

Build mock for downstreams. Mock sleep for 10 seconds and fail/success/complete.

Assume you get callbacks from the outbound call provider. (async so that we are efficient, non
blocking)
Multi queue / queue per campaign approach has drawbacks even with pooling instead why not handle it
in scheduling?

Campaign can have 1K to 100K phone numbers. Think about ingestion strategy.

Add scheduling as a strategy which can be tuned, e.g. Lets say remaining calls based scheduling,
priority based scheduling, etc.

All the third party services have to be local, use docker compose.