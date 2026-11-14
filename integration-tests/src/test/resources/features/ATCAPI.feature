Feature: ATC administration API functionalities

  Scenario: Tower admin adds a controller to their tower
    Given An ATC admin has details for a new controller
    When The ATC admin adds the controller
    Then The controller is added to the admin's tower
    When The ATC admin views the tower controllers
    Then The new controller is listed without controllers from another tower
