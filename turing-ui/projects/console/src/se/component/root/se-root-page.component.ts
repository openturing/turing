import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier-updated';
import { Router, RouterModule } from '@angular/router';

@Component({
    selector: 'se-root-page',
    templateUrl: './se-root-page.component.html',
    standalone: false
})
export class TurSERootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
