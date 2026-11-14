Feature: Airline and FAA airplane API functionalities

  Scenario: Airline admin registers an airplane and FAA admin approves activation
    Given An airline admin has a new airplane to register
    When The airline admin adds the airplane
    Then The airplane is added as grounded
    When The airline admin requests airplane activation
    Then The FAA admin can see the pending activation request
    When The FAA admin accepts the activation request
    Then The activation request is accepted and the airplane is active

  Scenario: FAA admin rejects an airplane activation request
    Given A new airplane has a pending activation request
    When The FAA admin denies the activation request
    Then The activation request is denied and the airplane remains grounded
