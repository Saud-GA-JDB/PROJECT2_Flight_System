Feature: Airport inspection API functionalities

  Scenario: Airport admin assigns an inspector who passes the inspection
    Given A flight is waiting for inspection
    When The airport admin assigns the inspector
    Then The airport admin can see the assigned inspection
    And The inspector can see their assigned inspection
    When The inspector passes the inspection
    Then The inspection is passed with its reason recorded

  Scenario: Inspector fails an inspection
    Given A flight has an assigned inspection
    When The inspector fails the inspection
    Then The inspection is failed with its reason recorded
