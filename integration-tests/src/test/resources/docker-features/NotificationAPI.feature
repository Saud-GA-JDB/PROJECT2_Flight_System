Feature: Airplane activation notifications

  Scenario: FAA admin receives an activation request notification
    Given an airline admin has a grounded airplane
    And an FAA admin is connected to notifications
    When the airline admin requests airplane activation
    Then the activation request is created successfully
    And the FAA admin receives its activation-requested notification
