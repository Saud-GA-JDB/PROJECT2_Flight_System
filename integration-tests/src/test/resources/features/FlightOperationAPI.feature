Feature: Pilot and ATC flight operation API functionalities

  Scenario: Pilot completes a flight with tower clearances
    Given A ready flight is assigned to the pilot
    Then The pilot can see the assigned flight
    When The pilot requests take-off clearance
    Then The origin controller can see the pending request
    When The origin controller approves take-off clearance
    Then Departure clearance is approved
    When The pilot takes off
    Then The flight is in the air and departure clearance is consumed
    When The pilot requests landing clearance
    And The destination controller approves landing clearance
    Then Arrival clearance is approved
    When The pilot lands
    Then The flight is closed and arrival clearance is consumed

  Scenario: Pilot cannot take off after the controller rejects clearance
    Given A ready flight is assigned to the pilot
    When The pilot requests take-off clearance
    And The origin controller rejects take-off clearance
    Then Departure clearance is rejected
    When The pilot tries to take off
    Then Take-off is rejected and the flight has not departed

  Scenario: Pilot cannot take off without clearance
    Given A ready flight is assigned to the pilot
    When The pilot tries to take off
    Then Take-off is rejected and the flight has not departed
