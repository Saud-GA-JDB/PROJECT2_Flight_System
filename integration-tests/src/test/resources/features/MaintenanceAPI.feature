Feature: Maintenance API functionalities

  Scenario: Airline requests maintenance and the provider completes it
    Given An airline has a contracted maintenance provider
    When The airline admin requests airplane maintenance
    Then The maintenance admin can see the pending request
    When The maintenance admin assigns the technician
    Then The technician can see their assigned maintenance
    When The technician completes the maintenance
    Then The maintenance is completed with the findings recorded
